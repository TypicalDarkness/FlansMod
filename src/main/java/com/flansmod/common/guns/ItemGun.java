package com.flansmod.common.guns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import org.lwjgl.input.Mouse;

import com.flansmod.client.ClientProxy;
import com.flansmod.client.FlansModClient;
import com.flansmod.client.FlansModResourceHandler;
import com.flansmod.client.debug.EntityDebugDot;
import com.flansmod.client.debug.EntityDebugVector;
import com.flansmod.client.model.GunAnimations;
import com.flansmod.client.model.InstantBulletRenderer;
import com.flansmod.client.model.InstantBulletRenderer.InstantShotTrail;
import com.flansmod.client.model.OverrideVanillaModelLoader;
import com.flansmod.client.model.RenderCustomItem;
import com.flansmod.common.FlansMod;
import com.flansmod.common.FlansUtils;
import com.flansmod.common.PlayerData;
import com.flansmod.common.PlayerHandler;
import com.flansmod.common.guns.ShotData.InstantShotData;
import com.flansmod.common.guns.ShotData.SpawnEntityShotData;
import com.flansmod.common.guns.raytracing.FlansModRaytracer;
import com.flansmod.common.guns.raytracing.FlansModRaytracer.BlockHit;
import com.flansmod.common.guns.raytracing.FlansModRaytracer.BulletHit;
import com.flansmod.common.guns.raytracing.FlansModRaytracer.DriveableHit;
import com.flansmod.common.guns.raytracing.FlansModRaytracer.EntityHit;
import com.flansmod.common.guns.raytracing.FlansModRaytracer.PlayerBulletHit;
import com.flansmod.common.network.PacketPlaySound;
import com.flansmod.common.network.PacketReload;
import com.flansmod.common.network.PacketShotData;
import com.flansmod.common.teams.EntityGunItem;
import com.flansmod.common.types.IPaintableItem;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.util.CTabs;
import com.flansmod.common.util.Config;
import com.flansmod.common.vector.Vector3f;
import com.google.common.collect.Multimap;

import net.fexcraft.mod.lib.api.item.IItem;
import net.fexcraft.mod.lib.util.item.ItemUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemGun extends Item implements IPaintableItem<GunType>, IItem
{
	private static final int CLIENT_TO_SERVER_UPDATE_INTERVAL = 1;
	private static final int SERVER_TO_CLIENT_UPDATE_INTERVAL = 2;
	
	private GunType type;

	@Override
	public GunType getInfoType() { return type; }


	private int soundDelay = 0;
	
	private static boolean rightMouseHeld;
	private static boolean lastRightMouseHeld;
	private static boolean leftMouseHeld;
	private static boolean lastLeftMouseHeld;

	private static boolean getMouseHeld(EnumHand hand)
	{
		//mouse has to depend on EnumHand and not EnumHandSide. If main hand is changed to left in mc-settings,
		//key-binding for mouse stays the same
		return hand == EnumHand.OFF_HAND ? leftMouseHeld : rightMouseHeld;
	}

	private static boolean getLastMouseHeld(EnumHand hand)
	{
		return hand == EnumHand.OFF_HAND ? lastLeftMouseHeld : lastRightMouseHeld;
	}
	
	private static List<ShotData> shotsFiredClient = new ArrayList<ShotData>(), shotsFiredServer = new ArrayList<ShotData>();
	
	public ItemGun(GunType type){
		maxStackSize = 1;
		this.type = type;
		type.item = this;
		setMaxDamage(0);
		setCreativeTab(CTabs.weapons);
		//GameRegistry.registerItem(this, type.shortName, FlansMod.MODID);
		ItemUtil.register(FlansMod.MODID, this);
		
		//moved code to another class cause client imports in the constructor crash the server
		if(FMLCommonHandler.instance().getSide() == Side.CLIENT){
			OverrideVanillaModelLoader.INSTANCE.setPaintJobIcons(this);
		}
	}

	@Override
	public RenderCustomItem getRenderItemEntity()
	{
		return ClientProxy.gunRenderer;
	}

	/** Get the bullet item stack stored in the gun's NBT data (the loaded magazine / bullets) */
	public ItemStack getBulletItemStack(ItemStack gun, int id)
	{
		//If the gun has no tags, give it some
		if(!gun.hasTagCompound())
		{
			gun.setTagCompound(new NBTTagCompound());
			return null;
		}
		//If the gun has no ammo tags, give it some
		if(!gun.getTagCompound().hasKey("ammo"))
		{
			NBTTagList ammoTagsList = new NBTTagList();
			for(int i = 0; i < type.numAmmoItemsInGun; i++)
			{
				ammoTagsList.appendTag(new NBTTagCompound());
			}
			gun.getTagCompound().setTag("ammo", ammoTagsList);
			return null;
		}
		//Take the list of ammo tags
		NBTTagList ammoTagsList = gun.getTagCompound().getTagList("ammo", Constants.NBT.TAG_COMPOUND);
		//Get the specific ammo tags required
		NBTTagCompound ammoTags = ammoTagsList.getCompoundTagAt(id);
		return ItemStack.loadItemStackFromNBT(ammoTags);
	}
	
	/** Set the bullet item stack stored in the gun's NBT data (the loaded magazine / bullets) */
	public void setBulletItemStack(ItemStack gun, ItemStack bullet, int id)
	{
		//If the gun has no tags, give it some
		if(!gun.hasTagCompound())
		{
			gun.setTagCompound(new NBTTagCompound());
		}
		//If the gun has no ammo tags, give it some
		if(!gun.getTagCompound().hasKey("ammo"))
		{
			NBTTagList ammoTagsList = new NBTTagList();
			for(int i = 0; i < type.numAmmoItemsInGun; i++)
			{
				ammoTagsList.appendTag(new NBTTagCompound());
			}
			gun.getTagCompound().setTag("ammo", ammoTagsList);
		}
		//Take the list of ammo tags
		NBTTagList ammoTagsList = gun.getTagCompound().getTagList("ammo", Constants.NBT.TAG_COMPOUND);
		//Get the specific ammo tags required
		NBTTagCompound ammoTags = ammoTagsList.getCompoundTagAt(id);
		//Represent empty slots by nulltypes
		if(bullet == null)
		{
			ammoTags = new NBTTagCompound();
		}
		//Set the tags to match the bullet stack
		bullet.writeToNBT(ammoTags);
	}

	/** Method for dropping items on reload and on shoot */
	public static void dropItem(World world, Entity entity, String itemName)
	{
		if (itemName != null)
		{
			int damage = 0;
			if (itemName.contains("."))
			{
				damage = Integer.parseInt(itemName.split("\\.")[1]);
				itemName = itemName.split("\\.")[0];
			}
			ItemStack dropStack = InfoType.getRecipeElement(itemName, damage);
			entity.entityDropItem(dropStack, 0.5F);
		}
	}
	
	/** Deployable guns only */
	@Override
	public ActionResult<ItemStack> onItemRightClick(ItemStack itemstack, World world, EntityPlayer entityplayer, EnumHand hand)
	{
		if (type.deployable)
		{
	    	//Raytracing
	        float cosYaw = MathHelper.cos(-entityplayer.rotationYaw * 0.01745329F - 3.141593F);
	        float sinYaw = MathHelper.sin(-entityplayer.rotationYaw * 0.01745329F - 3.141593F);
	        float cosPitch = -MathHelper.cos(-entityplayer.rotationPitch * 0.01745329F);
	        float sinPitch = MathHelper.sin(-entityplayer.rotationPitch * 0.01745329F);
	        double length = 5D;
	        Vec3d posVec = new Vec3d(entityplayer.posX, entityplayer.posY + 1.62D - entityplayer.getYOffset(), entityplayer.posZ);        
	        Vec3d lookVec = posVec.addVector(sinYaw * cosPitch * length, sinPitch * length, cosYaw * cosPitch * length);
	        RayTraceResult look = world.rayTraceBlocks(posVec, lookVec, true);
	        
	        //Result check
			if (look != null && look.typeOfHit == RayTraceResult.Type.BLOCK)
			{
				if (look.sideHit == EnumFacing.UP)
				{
					int playerDir = MathHelper.floor_double(((entityplayer.rotationYaw * 4F) / 360F) + 0.5D) & 3;
					int i = look.getBlockPos().getX();
					int j = look.getBlockPos().getY();
					int k = look.getBlockPos().getZ();
					if (!world.isRemote)
					{
						if (world.getBlockState(new BlockPos(i, j, k)).getBlock() == Blocks.SNOW)
						{
							j--;
						}
						if (isSolid(world, i, j, k) && (world.getBlockState(new BlockPos(i, j + 1, k)).getBlock() == Blocks.AIR || world.getBlockState(new BlockPos(i, j + 1, k)).getBlock() == Blocks.SNOW) && (world.getBlockState(new BlockPos(i + (playerDir == 1 ? 1 : 0) - (playerDir == 3 ? 1 : 0), j + 1, k - (playerDir == 0 ? 1 : 0) + (playerDir == 2 ? 1 : 0))).getBlock() == Blocks.AIR) && (world.getBlockState(new BlockPos(i + (playerDir == 1 ? 1 : 0) - (playerDir == 3 ? 1 : 0), j, k - (playerDir == 0 ? 1 : 0) + (playerDir == 2 ? 1 : 0))).getBlock() == Blocks.AIR || world.getBlockState(new BlockPos(i + (playerDir == 1 ? 1 : 0) - (playerDir == 3 ? 1 : 0), j, k - (playerDir == 0 ? 1 : 0) + (playerDir == 2 ? 1 : 0))).getBlock() == Blocks.SNOW))
						{
							for (EntityMG mg : EntityMG.mgs)
							{
								if (mg.blockX == i && mg.blockY == j + 1 && mg.blockZ == k && !mg.isDead)
									return new ActionResult(EnumActionResult.PASS, itemstack);
							}
							if(!world.isRemote)
							{
								EntityMG mg = new EntityMG(world, i, j + 1, k, playerDir, type);
								if(getBulletItemStack(itemstack, 0) != null)
								{
									mg.ammo = getBulletItemStack(itemstack, 0);
								}
								world.spawnEntityInWorld(mg);
								
							}
							if (!entityplayer.capabilities.isCreativeMode)
								itemstack.stackSize = 0;
						}
					}
				}
			}
		}
		//Stop the gun bobbing up and down when holding shoot and looking at a block
		if(world.isRemote)
		{
			for(int i = 0; i < 3; i++)
				Minecraft.getMinecraft().entityRenderer.itemRenderer.updateEquippedItem();
		}
		return new ActionResult(EnumActionResult.SUCCESS, itemstack);
	}
	
	// _____________________________________________________________________________
	//
	// Shooting code
	// _____________________________________________________________________________
	
	@SideOnly(Side.CLIENT)
	public void onUpdateClient(ItemStack gunstack, int gunSlot, World world, EntityLivingBase entity, EnumHandSide handSide)
	{
		if(!(entity instanceof EntityPlayer))
		{			
			return;
		}
		// Get useful objects
		Minecraft mc = Minecraft.getMinecraft();
		EntityPlayer player = (EntityPlayer)entity;
		PlayerData data = PlayerHandler.getPlayerData(player, Side.CLIENT);

		// Play idle sounds
		if (soundDelay <= 0 && type.idleSound != null)
		{
			PacketPlaySound.sendSoundPacket(entity.posX, entity.posY, entity.posZ, Config.soundRange, entity.dimension, type.idleSound, false);
			soundDelay = type.idleSoundLength;
		}
		
		// This code is not for deployables
		if (type.deployable)
			return;
		
		// Do not shoot ammo bags, flags or dropped gun items
		if(mc.objectMouseOver != null && (mc.objectMouseOver.entityHit instanceof EntityGunItem || (mc.objectMouseOver.entityHit instanceof EntityGrenade && ((EntityGrenade)mc.objectMouseOver.entityHit).type.isDeployableBag)))
			return;
		
		// If we have an off hand item, then disable our secondary functions
		//boolean secondaryFunctionsEnabled = true;
		
		// Update off hand cycling. Controlled by the main gun, since it is always around.
		/*
		if(!isOffHand && type.oneHanded)
		{				
			//Cycle selection
			int dWheel = Mouse.getDWheel();
			if(Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()) && dWheel != 0)
			{
				data.cycleOffHandItem(player, dWheel);
			}
		}
		*/
		
		if(type.usableByPlayers)
		{
			EnumHand hand = FlansUtils.getHandForSide(handSide, entity);

			if (hand == EnumHand.MAIN_HAND)
			{
				lastRightMouseHeld = rightMouseHeld;
				rightMouseHeld = Mouse.isButtonDown(1);
			}
			else
			{
				lastLeftMouseHeld = leftMouseHeld;
				leftMouseHeld = Mouse.isButtonDown(0);
			}

			boolean needsToReload = needsToReload(gunstack);
			boolean shouldShootThisTick = false;
			switch(type.getFireMode(gunstack))
			{
				case BURST:
				{
					if(data.getBurstRoundsRemaining(handSide) > 0)
					{
						shouldShootThisTick = true;
					}
					// Fallthrough to semi auto
				}
				case SEMIAUTO:
				{
					if(getMouseHeld(hand) && !getLastMouseHeld(hand))
					{
						shouldShootThisTick = true;
					}
					else needsToReload = false;
					break;
				}
				case MINIGUN:
				{
					if(needsToReload)
					{
						needsToReload = getMouseHeld(hand);
						break;
					}
					if(getMouseHeld(hand))
					{
						data.minigunSpeed += 2.0f;
						data.minigunSpeed *= 0.9f;
						// TODO : Re-add looping sounds
						if(data.minigunSpeed < type.minigunStartSpeed)
						{
							if(type.useLoopingSounds)
							{
								data.shouldPlayWarmupSound = true;
							}
							break;
						}
					}
					else if(data.minigunSpeed > 0.0f)
					{
						data.shouldPlayCooldownSound = true;
					}
						
					//else fallthrough to full auto
				}
				case FULLAUTO:
				{
					shouldShootThisTick = getMouseHeld(hand);
					if(!shouldShootThisTick)
					{
						needsToReload = false;
					}
					break;
				}
				default:
					needsToReload = false;
					break;
			}
			
			// Do reload if we pressed fire.
			if(needsToReload)
			{
				if(Reload(gunstack, world, player, player.inventory, handSide, false, player.capabilities.isCreativeMode))
				{
					//Set player shoot delay to be the reload delay
					//Set both gun delays to avoid reloading two guns at once
					data.shootTimeRight = data.shootTimeLeft = (int)type.getReloadTime(gunstack);
					
					GunAnimations animations = FlansModClient.getGunAnimations(player, handSide);

					int pumpDelay = type.model == null ? 0 : type.model.pumpDelayAfterReload;
					int pumpTime = type.model == null ? 1 : type.model.pumpTime;
					animations.doReload(type.reloadTime, pumpDelay, pumpTime);
					
					if(handSide == EnumHandSide.LEFT)
					{
						data.reloadingLeft = true;
						data.burstRoundsRemainingLeft = 0;
					}
					else
					{
						data.reloadingRight = true;
						data.burstRoundsRemainingRight = 0;
					}
					//Send reload packet to server
					FlansMod.getPacketHandler().sendToServer(new PacketReload(handSide, false));
				}
			}
			// Fire!
			else if(shouldShootThisTick)
			{
				float shootTime = data.getShootTime(handSide);
									
				// For each 
				while(shootTime <= 0.0f)
				{
					// Add the delay for this shot and shoot it!
					shootTime += type.shootDelay;
					
					ItemStack shootableStack = getBestNonEmptyShootableStack(gunstack);
					ItemShootable shootableItem = (ItemShootable)shootableStack.getItem();
					ShootableType shootableType = shootableItem.type;
					// Instant bullets. Do a raytrace
					if(type.bulletSpeed == 0.0f)
					{
						for(int i = 0; i < type.numBullets * shootableType.numBullets; i++)
						{
							Vector3f rayTraceOrigin = new Vector3f(player.getPositionEyes(0.0f));
							Vector3f rayTraceDirection = new Vector3f(player.getLookVec());
							
							float spread = 0.005f * type.getSpread(gunstack) * shootableType.bulletSpread;
							
							rayTraceDirection.x += (float)world.rand.nextGaussian() * spread;
							rayTraceDirection.y += (float)world.rand.nextGaussian() * spread;
							rayTraceDirection.z += (float)world.rand.nextGaussian() * spread;
							
							rayTraceDirection.scale(500.0f);
							
							List<BulletHit> hits = FlansModRaytracer.Raytrace(world, player, false, null, rayTraceOrigin, rayTraceDirection, 0);
							//Entity victim = null;
							Vector3f hitPos = Vector3f.add(rayTraceOrigin, rayTraceDirection, null);
							BulletHit firstHit = null;
							if(!hits.isEmpty())
							{
								firstHit = hits.get(0);
								hitPos = Vector3f.add(rayTraceOrigin, (Vector3f)rayTraceDirection.scale(firstHit.intersectTime), null);
								//victim = firstHit.GetEntity();
							}
							
							Vector3f gunOrigin = FlansModRaytracer.GetPlayerMuzzlePosition(player, handSide);
							
							if(FlansMod.DEBUG)
							{
								world.spawnEntityInWorld(new EntityDebugDot(world, gunOrigin, 100, 1.0f, 1.0f, 1.0f));
							}
	
							ShotData shotData = new InstantShotData(hand, gunSlot, type, shootableType, player, gunOrigin, firstHit, hitPos, type.getDamage(gunstack), i < type.numBullets * shootableType.numBullets - 1);
							shotsFiredClient.add(shotData);
						}
					}
					// Else, spawn an entity
					else
					{
						ShotData shotData = new SpawnEntityShotData(hand, gunSlot, type, shootableType, player, new Vector3f(player.getLookVec()));
						shotsFiredClient.add(shotData);
					}

					// Now do client side things
					GunAnimations animations = FlansModClient.getGunAnimations(player, handSide);
					
					int pumpDelay = type.model == null ? 0 : type.model.pumpDelay;
					int pumpTime = type.model == null ? 1 : type.model.pumpTime;
					animations.doShoot(pumpDelay, pumpTime);
					FlansModClient.playerRecoil += type.getRecoil(gunstack);
					if(type.consumeGunUponUse)
						player.inventory.setInventorySlotContents(gunSlot, null);
					
					// Update burst fire
					if(type.getFireMode(gunstack) == EnumFireMode.BURST)
					{
						int burstRoundsRemaining = data.getBurstRoundsRemaining(handSide);

						if(burstRoundsRemaining > 0)
							burstRoundsRemaining--;
						else burstRoundsRemaining = type.numBurstRounds;
						
						data.setBurstRoundsRemaining(handSide, burstRoundsRemaining);
					}
				}
				
				data.setShootTime(handSide, shootTime);
			}
			
			Vector3f gunOrigin = FlansModRaytracer.GetPlayerMuzzlePosition(player, handSide);
			
			if(FlansMod.DEBUG)
			{
				world.spawnEntityInWorld(new EntityDebugDot(world, gunOrigin, 100, 1.0f, 1.0f, 1.0f));
			}
			
			// Now send shooting data to the server
			if(!shotsFiredClient.isEmpty() && player.ticksExisted % CLIENT_TO_SERVER_UPDATE_INTERVAL == 0)
			{
				FlansMod.getPacketHandler().sendToServer(new PacketShotData(shotsFiredClient));
				shotsFiredClient.clear();
			}
			
			// Check for scoping in / out
			//TODO scoping
			/*
			IScope currentScope = type.getCurrentScope(gunstack);
			if(!isOffHand && !hasOffHand && leftMouseHeld && !lastLeftMouseHeld
					&& (type.secondaryFunction == EnumSecondaryFunction.ADS_ZOOM || type.secondaryFunction == EnumSecondaryFunction.ZOOM) )
			{
				FlansModClient.SetScope(currentScope);
			}
			*/
		}
		
		// And finally do sounds
		if (soundDelay > 0)
		{
			soundDelay--;
		}
	}
	
	public void ServerHandleShotData(ItemStack gunstack, int gunSlot, World world, Entity entity, ShotData shotData)
	{
		// Get useful things
		if(!(entity instanceof EntityPlayerMP))
		{
			return;
		}
		EntityPlayerMP player = (EntityPlayerMP)entity;
		PlayerData data = PlayerHandler.getPlayerData(player, Side.SERVER);
		if(data == null)
		{
			return;
		}
		
		
		boolean isExtraBullet = shotData instanceof ShotData.InstantShotData && ((ShotData.InstantShotData) shotData).isExtraBullet;

		//Go through the bullet stacks in the gun and see if any of them are not null
		int bulletID = 0;
		ItemStack bulletStack = null;
		for(; bulletID < type.numAmmoItemsInGun; bulletID++)
		{
			ItemStack checkingStack = getBulletItemStack(gunstack, bulletID);
			if(checkingStack != null && checkingStack.getItemDamage() < checkingStack.getMaxDamage())
			{
				bulletStack = checkingStack;
				break;
			}
		}

		// We have no bullet stack. So we need to reload. The player will send us a message requesting we do a reload
		if(bulletStack == null)
		{
			return;
		}
		
		if(bulletStack.getItem() instanceof ItemShootable)
		{
			ShootableType bullet = ((ItemShootable)bulletStack.getItem()).type;
			
			if(!isExtraBullet)
			{				
				// Drop item on shooting if bullet requires it
				if(bullet.dropItemOnShoot != null && !player.capabilities.isCreativeMode)
					dropItem(world, player, bullet.dropItemOnShoot);
				// Drop item on shooting if gun requires it
				if(type.dropItemOnShoot != null)// && !entityplayer.capabilities.isCreativeMode)
					dropItem(world, player, type.dropItemOnShoot);
				
				if(type.knockback > 0)
				{
					//TODO : Apply knockback		
				}	
				
				//Damage the bullet item
				bulletStack.setItemDamage(bulletStack.getItemDamage() + 1);
				
				//Update the stack in the gun
				setBulletItemStack(gunstack, bulletStack, bulletID);
				
				if(type.consumeGunUponUse && gunSlot != -1)
					player.inventory.setInventorySlotContents(gunSlot, null);
			}
			
			// Spawn an entity, classic style
			if(shotData instanceof SpawnEntityShotData)
			{
				// Play a sound if the previous sound has finished
				if (soundDelay <= 0 && type.shootSound != null)
				{
					AttachmentType barrel = type.getBarrel(gunstack);
					boolean silenced = barrel != null && barrel.silencer;
					//world.playSoundAtEntity(entityplayer, type.shootSound, 10F, type.distortSound ? 1.0F / (world.rand.nextFloat() * 0.4F + 0.8F) : 1.0F);
					PacketPlaySound.sendSoundPacket(player.posX, player.posY, player.posZ, Config.soundRange, player.dimension, type.shootSound, type.distortSound, silenced);
					soundDelay = type.shootSoundLength;
				}
				
				//Shoot
				// Spawn the bullet entities
				for (int k = 0; k < type.numBullets * bullet.numBullets; k++)
				{
					// Actually shoot the bullet
					((ItemShootable)bulletStack.getItem()).Shoot(world, 
							new Vector3f(player.posX, player.posY + player.getEyeHeight(), player.posZ), 
							new Vector3f(player.getLookVec()), 
							type.getDamage(gunstack), 
							(player.isSneaking() ? 0.7F : 1F) * type.getSpread(gunstack) * bullet.bulletSpread,
							type.getBulletSpeed(gunstack), 
							type, 
							player);
				}
			}
			// Do a raytrace check on what they've sent us.
			else if(shotData instanceof InstantShotData)
			{
				InstantShotData instantData = (InstantShotData) shotData;
				//if(stuff)
				//{
				//	calculate our own raytrace to verify they're not cheating
				//}
				// else
				{
					// Take a point halfway along. Then make the radius encapsulate both ends and then some
					Vector3f targetPoint = Vector3f.add(instantData.origin, instantData.hitPos, null);
					targetPoint.scale(0.5f);
					//float radius = Vector3f.sub(instantData.origin, instantData.hitPos, null).length();
					//radius += 50.0f;
					
					doInstantShot(world, player, type, (BulletType)bullet, instantData.origin, instantData.hitPos, instantData.hitData, type.getDamage(gunstack));
					
					shotsFiredServer.add(shotData);
				}
			}
		}
	}
	
	@SideOnly(Side.CLIENT)
	private void PlayShotSound(World world, boolean silenced, float x, float y, float z)
	{
		FMLClientHandler.instance().getClient().getSoundHandler().playSound(new PositionedSoundRecord(
				new SoundEvent(FlansModResourceHandler.getSound(type.shootSound)),
				SoundCategory.NEUTRAL,
				silenced ? 5F : 10F,
				(type.distortSound ? 1.0F / (world.rand.nextFloat() * 0.4F + 0.8F) : 1.0F) * (silenced ? 2F : 1F),
				new BlockPos(x, y, z)
		));

	}
	public void doInstantShot(World world, Entity shooter, InfoType shotFrom, BulletType shotType, Vector3f origin, Vector3f hit, BulletHit hitData, float damage)
	{
		if(EntityBullet.OnHit(world, origin, hit, shooter, shotFrom, shotType, null, damage, hitData))
		{
			EntityBullet.OnDetonate(world, hit, shooter, null, shotFrom, shotType);
		}
		
		if(world.isRemote)
		{
			// Play a sound if the previous sound has finished
			if (soundDelay <= 0 && type.shootSound != null)
			{
				//AttachmentType barrel = type.getBarrel(gunstack);
				boolean silenced = false;//barrel != null && barrel.silencer;
				
				PlayShotSound(world, silenced, (float)shooter.posX, (float)shooter.posY, (float)shooter.posZ);

				soundDelay = type.shootSoundLength;
			}
			
			if(FlansMod.DEBUG)
			{
				world.spawnEntityInWorld(new EntityDebugVector(world, origin, Vector3f.sub(hit, origin, null), 100, 0.5f, 0.5f, 1.0f));
			}
			
			InstantBulletRenderer.AddTrail(new InstantShotTrail(origin, hit, (BulletType)shotType));
			
			if(hitData instanceof BlockHit)
			{
				BlockHit blockHit = (BlockHit)hitData;
				
				//BlockPos blockPos = blockHit.raytraceResult.getBlockPos();
				IBlockState blockState = world.getBlockState(blockHit.raytraceResult.getBlockPos());
				
				//Vec3i normal = blockHit.raytraceResult.sideHit.getDirectionVec();
				
				if(blockState != null)
				{
					for(int i = 0; i < 2; i++)
					{
						//TODO
		                /*EntityFX fx = Minecraft.getMinecraft().effectRenderer.spawnEffectParticle(
		                		EnumParticleTypes.BLOCK_CRACK.getParticleID(), hit.x, hit.y, hit.z, 0.0f, 0.0f, 0.0f, 
		                		Block.getIdFromBlock(blockState.getBlock()));
		                
		                double scale = world.rand.nextGaussian() * 0.1d + 0.5d;
		                
		                fx.motionX = (double)normal.getX() * scale + world.rand.nextGaussian() * 0.025d;
		                fx.motionY = (double)normal.getY() * scale + world.rand.nextGaussian() * 0.025d;
		                fx.motionZ = (double)normal.getZ() * scale + world.rand.nextGaussian() * 0.025d;
		                
	             		if(Minecraft.getMinecraft().gameSettings.fancyGraphics)
	             			fx.renderDistanceWeight = 100D;*/
					}
				}
			}
			
			if(world.isRemote)
			{
				if(shooter == Minecraft.getMinecraft().thePlayer)
				{
					if(hitData instanceof EntityHit || hitData instanceof PlayerBulletHit || hitData instanceof DriveableHit)
					{
						// Add a hit marker
						FlansModClient.AddHitMarker();
					}
				}
			}
		}
		else
		{

		}
	}
	
	public void onUpdateServer(ItemStack itemstack, int gunSlot, World world, Entity entity, EnumHandSide side)
	{
		if(!(entity instanceof EntityPlayerMP))
		{
			return;
		}
		EntityPlayerMP player = (EntityPlayerMP)entity;
		PlayerData data = PlayerHandler.getPlayerData(player);
		if(data == null)
			return;

		/*
		if(player.inventory.getCurrentItem() != itemstack)
		{
			//If the player is no longer holding a gun, emulate a release of the shoot button
			if(player.inventory.getCurrentItem() == null || player.inventory.getCurrentItem().getItem() == null || !(player.inventory.getCurrentItem().getItem() instanceof ItemGun))
			{
				data.isShootingRight = data.isShootingLeft = false;
				//data.offHandGunSlot = 0;
				(new PacketSelectOffHandGun(0)).handleServerSide(player);
			}
			return;
		}
		*/
		
		if(!shotsFiredServer.isEmpty())// && entity.ticksExisted % SERVER_TO_CLIENT_UPDATE_INTERVAL == 0)
		{
			FlansMod.getPacketHandler().sendToDimension(new PacketShotData(shotsFiredServer), player.dimension );
			shotsFiredServer.clear();
		}
	}
	
	/** Generic update method. If we have an off hand weapon, it will also make calls for that 
	 *  Passes on to onUpdateEach */
	@Override
	public void onUpdate(ItemStack itemstack, World world, Entity entity, int i, boolean flag){
		if(entity instanceof EntityPlayer){
			
			EntityPlayer player = (EntityPlayer) entity;
			EnumHand hand;
			if(itemstack == player.getHeldItemMainhand()){
				hand = EnumHand.MAIN_HAND;
			}
			else if(itemstack == player.getHeldItemOffhand()){
				hand = EnumHand.OFF_HAND;
			}
			else{
				return;
			}
			
			EnumHandSide side = FlansUtils.getSideForHand(hand, player);
			//don't shoot if gui is open
			if(world.isRemote){
				if(Minecraft.getMinecraft().currentScreen != null){
					rightMouseHeld = false;
					leftMouseHeld = false;
					return;
				}
			}

			//onUpdate is called for offHand as well now, do mouse related stuff in onUpdateClient.
			/*
			if (world.isRemote)
			{
				// Get button presses. Do this before splitting into each hand. Prevents second pass wiping the data
				lastRightMouseHeld = rightMouseHeld;
				lastLeftMouseHeld = leftMouseHeld;
				//TODO key-binding
				rightMouseHeld = Mouse.isButtonDown(1);
				leftMouseHeld = Mouse.isButtonDown(0);
			}
			*/
			
			onUpdateEach(itemstack, player.inventory.currentItem, world, player, side);
		}
	}
	
	/** Called once for each weapon we are weilding */
	private void onUpdateEach(ItemStack itemstack, int gunSlot, World world, EntityLivingBase entity, EnumHandSide side)
	{
		if(world.isRemote)
			onUpdateClient(itemstack, gunSlot, world, entity, side);
		else onUpdateServer(itemstack, gunSlot, world, entity, side);
	}

	public boolean Reload(ItemStack gunstack, World world, Entity entity, IInventory inventory, EnumHandSide hand, boolean forceReload, boolean isCreative)
	{
		//Deployable guns cannot be reloaded in the inventory
		if(type.deployable)
			return false;
		//If you cannot reload half way through a clip, reject the player for trying to do so
		if(forceReload && !type.canForceReload)
			return false;
		
		//For playing sounds afterwards
		boolean reloadedSomething = false;
		//Check each ammo slot, one at a time
		for(int i = 0; i < type.numAmmoItemsInGun; i++)
		{
			//Get the stack in the slot
			ItemStack bulletStack = getBulletItemStack(gunstack, i);
			
			//If there is no magazine, if the magazine is empty or if this is a forced reload
			if(bulletStack == null || bulletStack.getItemDamage() == bulletStack.getMaxDamage() || forceReload)
			{		
				//Iterate over all inventory slots and find the magazine / bullet item with the most bullets
				int bestSlot = -1;
				int bulletsInBestSlot = 0;
				for (int j = 0; j < inventory.getSizeInventory(); j++)
				{
					ItemStack item = inventory.getStackInSlot(j);
					if (item != null && item.getItem() instanceof ItemShootable && type.isAmmo(((ItemShootable)(item.getItem())).type))
					{
						int bulletsInThisSlot = item.getMaxDamage() - item.getItemDamage();
						if(bulletsInThisSlot > bulletsInBestSlot)
						{
							bestSlot = j;
							bulletsInBestSlot = bulletsInThisSlot;
						}
					}
				}
				//If there was a valid non-empty magazine / bullet item somewhere in the inventory, load it
				if(bestSlot != -1)
				{
					ItemStack newBulletStack = inventory.getStackInSlot(bestSlot);
					//ShootableType newBulletType = ((ItemShootable)newBulletStack.getItem()).type;
					
					//Unload the old magazine (Drop an item if it is required and the player is not in creative mode)
					if(bulletStack != null && bulletStack.getItem() instanceof ItemShootable && ((ItemShootable)bulletStack.getItem()).type.dropItemOnReload != null && !isCreative && bulletStack.getItemDamage() == bulletStack.getMaxDamage())
					{
						if(!world.isRemote)
							dropItem(world, entity, ((ItemShootable)bulletStack.getItem()).type.dropItemOnReload);
					}
						
					//The magazine was not finished, pull it out and give it back to the player or, failing that, drop it
					if(bulletStack != null && bulletStack.getItemDamage() < bulletStack.getMaxDamage())
					{
						if(!InventoryHelper.addItemStackToInventory(inventory, bulletStack, isCreative))
						{
							if(!world.isRemote)
								entity.entityDropItem(bulletStack, 0.5F);
						}
					}
							
					//Load the new magazine
					ItemStack stackToLoad = newBulletStack.copy();
					stackToLoad.stackSize = 1;
					setBulletItemStack(gunstack, stackToLoad, i);					
					
					//Remove the magazine from the inventory
					if(!isCreative)
						newBulletStack.stackSize--;
					if(newBulletStack.stackSize <= 0)
						newBulletStack = null;
					inventory.setInventorySlotContents(bestSlot, newBulletStack);
								
					
					//Tell the sound player that we reloaded something
					reloadedSomething = true;
				}
			}
		}
		return reloadedSomething;
	}
	
	// TODO : All this bunk
		
	/* Melee MESS
	 * 	@Override
	public void onUpdate(ItemStack itemstack, World world, Entity pEnt, int i, boolean flag)
	{
		if(world.isRemote)
			onUpdateClient(itemstack, world, pEnt, i, flag);
		else onUpdateServer(itemstack, world, pEnt, i, flag);
		
		if(pEnt instanceof EntityPlayer)
		{
			EntityPlayer player = (EntityPlayer)pEnt;
			PlayerData data = PlayerHandler.getPlayerData(player);
			if(data == null)
				return;
			//if(data.lastMeleePositions == null || data.lastMeleePositions.length != type.meleeDamagePoints.size())
			//{
			//	data.lastMeleePositions = new Vector3f[type.meleeDamagePoints.size()];
			//	for(int j = 0; j < type.meleeDamagePoints.size(); j++)
			//		data.lastMeleePositions[j] = new Vector3f(player.posX, player.posY, player.posZ);
			//}
			//Melee weapon
			if(data.meleeLength > 0 && type.meleePath.size() > 0 && player.inventory.getCurrentItem() == itemstack)
			{
				for(int k = 0; k < type.meleeDamagePoints.size(); k++)
				{
					Vector3f meleeDamagePoint = type.meleeDamagePoints.get(k);
					//Do a raytrace from the prev pos to the current pos and attack anything in the way
					Vector3f nextPos = type.meleePath.get((data.meleeProgress + 1) % type.meleePath.size());
					Vector3f nextAngles = type.meleePathAngles.get((data.meleeProgress + 1) % type.meleePathAngles.size());
					RotatedAxes nextAxes = new RotatedAxes().rotateGlobalRoll(-nextAngles.x).rotateGlobalPitch(-nextAngles.z).rotateGlobalYaw(-nextAngles.y);
					
					Vector3f nextPosInGunCoords = nextAxes.findLocalVectorGlobally(meleeDamagePoint);
					Vector3f.add(nextPos, nextPosInGunCoords, nextPosInGunCoords);
					Vector3f.add(new Vector3f(0F, 0F, 0F), nextPosInGunCoords, nextPosInGunCoords);
					Vector3f nextPosInPlayerCoords = new RotatedAxes(player.rotationYaw + 90F, player.rotationPitch, 0F).findLocalVectorGlobally(nextPosInGunCoords);
					
					
					if(!FlansMod.proxy.isThePlayer(player))
						nextPosInPlayerCoords.y += 1.6F;
					
					Vector3f nextPosInWorldCoords = new Vector3f(player.posX + nextPosInPlayerCoords.x, player.posY + nextPosInPlayerCoords.y, player.posZ + nextPosInPlayerCoords.z);
					
					Vector3f dPos = data.lastMeleePositions[k] == null ? new Vector3f() : Vector3f.sub(nextPosInWorldCoords, data.lastMeleePositions[k], null);
					
					if(player.worldObj.isRemote && FlansMod.DEBUG)
						player.worldObj.spawnEntityInWorld(new EntityDebugVector(player.worldObj, data.lastMeleePositions[k], dPos, 200, 1F, 0F, 0F));
					
					//Do the raytrace
					{
						//Create a list for all bullet hits
						ArrayList<BulletHit> hits = new ArrayList<BulletHit>();
										
						//Iterate over all entities
						for(int j = 0; j < world.loadedEntityList.size(); j++)
						{
							Object obj = world.loadedEntityList.get(j);
							//Get players
							if(obj instanceof EntityPlayer)
							{
								EntityPlayer otherPlayer = (EntityPlayer)obj;
								PlayerData otherData = PlayerHandler.getPlayerData(otherPlayer);
								boolean shouldDoNormalHitDetect = false;
								if(otherPlayer == player)
									continue;
								if(otherData != null)
								{
									if(otherPlayer.isDead || otherData.team == Team.spectators)
									{
										continue;
									}
									int snapshotToTry = player instanceof EntityPlayerMP ? ((EntityPlayerMP)player).ping / 50 : 0;
									if(snapshotToTry >= otherData.snapshots.length)
										snapshotToTry = otherData.snapshots.length - 1;
									
									PlayerSnapshot snapshot = otherData.snapshots[snapshotToTry];
									if(snapshot == null)
										snapshot = otherData.snapshots[0];
									
									//DEBUG
									//snapshot = new PlayerSnapshot(player);
									
									//Check one last time for a null snapshot. If this is the case, fall back to normal hit detection
									if(snapshot == null)
										shouldDoNormalHitDetect = true;
									else
									{
										//Raytrace
										ArrayList<BulletHit> playerHits = snapshot.raytrace(data.lastMeleePositions[k] == null ? nextPosInWorldCoords : data.lastMeleePositions[k], dPos);
										hits.addAll(playerHits);
									}
								}
								
								//If we couldn't get a snapshot, use normal entity hitbox calculations
								if(otherData == null || shouldDoNormalHitDetect)
								{
									MovingObjectPosition mop = data.lastMeleePositions[k] == null ? player.getEntityBoundingBox().calculateIntercept(nextPosInWorldCoords.toVec3(), new Vec3(0F, 0F, 0F)) : player.getBoundingBox().calculateIntercept(data.lastMeleePositions[k].toVec3(), nextPosInWorldCoords.toVec3());
									if(mop != null)
									{
										Vector3f hitPoint = new Vector3f(mop.hitVec.xCoord - data.lastMeleePositions[k].x, mop.hitVec.yCoord - data.lastMeleePositions[k].y, mop.hitVec.zCoord - data.lastMeleePositions[k].z);
										float hitLambda = 1F;
										if(dPos.x != 0F)
											hitLambda = hitPoint.x / dPos.x;
										else if(dPos.y != 0F)
											hitLambda = hitPoint.y / dPos.y;
										else if(dPos.z != 0F)
											hitLambda = hitPoint.z / dPos.z;
										if(hitLambda < 0)
											hitLambda = -hitLambda;
										
										hits.add(new PlayerBulletHit(new PlayerHitbox(otherPlayer, new RotatedAxes(), new Vector3f(), new Vector3f(), new Vector3f(), EnumHitboxType.BODY), hitLambda));
									}
								}
							}
							else
							{
								Entity entity = (Entity)obj;
								if(entity != player && !entity.isDead && (entity instanceof EntityLivingBase || entity instanceof EntityAAGun))
								{
									MovingObjectPosition mop = entity.getEntityBoundingBox().calculateIntercept(data.lastMeleePositions[k].toVec3(), nextPosInWorldCoords.toVec3());
									if(mop != null)
									{
										Vector3f hitPoint = new Vector3f(mop.hitVec.xCoord - data.lastMeleePositions[k].x, mop.hitVec.yCoord - data.lastMeleePositions[k].y, mop.hitVec.zCoord - data.lastMeleePositions[k].z);
										float hitLambda = 1F;
										if(dPos.x != 0F)
											hitLambda = hitPoint.x / dPos.x;
										else if(dPos.y != 0F)
											hitLambda = hitPoint.y / dPos.y;
										else if(dPos.z != 0F)
											hitLambda = hitPoint.z / dPos.z;
										if(hitLambda < 0)
											hitLambda = -hitLambda;
										
										hits.add(new EntityHit(entity, hitLambda));
									}
								}
							}
						}
						
						//We hit something
						if(!hits.isEmpty())
						{
							//Sort the hits according to the intercept position
							Collections.sort(hits);
							
							float swingDistance = dPos.length();
							
							for(BulletHit bulletHit : hits)
							{
								if(bulletHit instanceof PlayerBulletHit)
								{
									PlayerBulletHit playerHit = (PlayerBulletHit)bulletHit;
									float damageMultiplier = 1F;
									switch(playerHit.hitbox.type)
									{
									case LEFTITEM : case RIGHTITEM : //Hit a shield. Stop the swing. 
									{
										data.meleeProgress = data.meleeLength = 0;
										return;
									}
									case HEAD : damageMultiplier = 2F; break;
									case RIGHTARM : case LEFTARM : damageMultiplier = 0.6F; break;
									default :
									}
									
									if(playerHit.hitbox.player.attackEntityFrom(getMeleeDamage(player), swingDistance * type.meleeDamage))
									{
										//If the attack was allowed, we should remove their immortality cooldown so we can shoot them again. Without this, any rapid fire gun become useless
										playerHit.hitbox.player.arrowHitTimer++;
										playerHit.hitbox.player.hurtResistantTime = playerHit.hitbox.player.maxHurtResistantTime / 2;
									}
									
									if(FlansMod.DEBUG)
										world.spawnEntityInWorld(new EntityDebugDot(world, new Vector3f(data.lastMeleePositions[k].x + dPos.x * playerHit.intersectTime, data.lastMeleePositions[k].y + dPos.y * playerHit.intersectTime, data.lastMeleePositions[k].z + dPos.z * playerHit.intersectTime), 1000, 1F, 0F, 0F));
								}
								else if(bulletHit instanceof EntityHit)
								{
									EntityHit entityHit = (EntityHit)bulletHit;
									if(entityHit.entity.attackEntityFrom(DamageSource.causePlayerDamage(player), swingDistance * type.meleeDamage) && entityHit.entity instanceof EntityLivingBase)
									{
										EntityLivingBase living = (EntityLivingBase)entityHit.entity;
										//If the attack was allowed, we should remove their immortality cooldown so we can shoot them again. Without this, any rapid fire gun become useless
										living.arrowHitTimer++;
										living.hurtResistantTime = living.maxHurtResistantTime / 2;
									}
									
									if(FlansMod.DEBUG)
										world.spawnEntityInWorld(new EntityDebugDot(world, new Vector3f(data.lastMeleePositions[k].x + dPos.x * entityHit.intersectTime, data.lastMeleePositions[k].y + dPos.y * entityHit.intersectTime, data.lastMeleePositions[k].z + dPos.z * entityHit.intersectTime), 1000, 1F, 0F, 0F));
								}
							}	
						}
					}
					//End raytrace
					
					data.lastMeleePositions[k] = nextPosInWorldCoords;
				}
				
				//Increment the progress meter
				data.meleeProgress++;
				//If we are done, reset the counters
				if(data.meleeProgress == data.meleeLength)
					data.meleeProgress = data.meleeLength = 0;
			}
		}
	}
	 
	 * 
	 */
	
	private boolean needsToReload(ItemStack stack)
	{
		for(int i = 0; i < type.numAmmoItemsInGun; i++)
		{
			ItemStack bulletStack = getBulletItemStack(stack, i);
			if(bulletStack != null  && bulletStack.getItemDamage() < bulletStack.getMaxDamage())
			{
				return false;
			}
		}
		return true;
	}
	
	public boolean CanReload(ItemStack gunstack, IInventory inventory)
	{
		for(int i = 0; i < inventory.getSizeInventory(); i++)
		{
			ItemStack stack = inventory.getStackInSlot(i);
			if(type.isAmmo(stack))
			{
				return true;
			}
		}
		return false;
	}
	
	private ItemStack getBestNonEmptyShootableStack(ItemStack stack)
	{
		for(int i = 0; i < type.numAmmoItemsInGun; i++)
		{
			ItemStack shootableStack = getBulletItemStack(stack, i);
			if(shootableStack != null && shootableStack.getItem() != null && shootableStack.getItemDamage() < shootableStack.getMaxDamage())
			{
				return shootableStack;
			}
		}
		return null;
	}
		
	
	// _____________________________________________________________________________
	//
	// Minecraft base item overrides
	// _____________________________________________________________________________

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> lines, boolean advancedTooltips)
	{
		if(type.description != null)
		{
			Collections.addAll(lines, type.description.split("_"));
		}
		if(type.showDamage)
			lines.add("\u00a79Damage" + "\u00a77: " + type.getDamage(stack));
		if(type.showRecoil)
			lines.add("\u00a79Recoil" + "\u00a77: " + type.getRecoil(stack));
		if(type.showSpread)
			lines.add("\u00a79Accuracy" + "\u00a77: " + type.getSpread(stack));
		if(type.showReloadTime)
			lines.add("\u00a79Reload Time" + "\u00a77: " + type.getReloadTime(stack) / 20 + "s");
		for(AttachmentType attachment : type.getCurrentAttachments(stack))
		{
			if(type.showAttachments)
			{
				String line = attachment.name;
				lines.add(line);
			}
		}
		for(int i = 0; i < type.numAmmoItemsInGun; i++)
		{
			ItemStack bulletStack = getBulletItemStack(stack, i);
			if(bulletStack != null && bulletStack.getItem() instanceof ItemBullet)
			{
				BulletType bulletType = ((ItemBullet)bulletStack.getItem()).type;					
				//String line = bulletType.name + (bulletStack.getMaxDamage() == 1 ? "" : " " + (bulletStack.getMaxDamage() - bulletStack.getItemDamage()) + "/" + bulletStack.getMaxDamage());
				String line = bulletType.name + " " + (bulletStack.getMaxDamage() - bulletStack.getItemDamage()) + "/" + bulletStack.getMaxDamage();
				lines.add(line);
			}
		}
	}
	
	@Override
	/** Make sure client and server side NBTtags update */
	public boolean getShareTag()
	{
		return true;
	}
	
	public DamageSource getMeleeDamage(EntityPlayer attacker)
	{
		return new EntityDamageSourceGun(type.shortName, attacker, attacker, type, false);
	}
	
	private boolean isSolid(World world, int i, int j, int k)
	{
		IBlockState block = world.getBlockState(new BlockPos(i, j, k));
		if (block.getBlock() == null)
			return false;
		return block.getMaterial().isSolid() && block.isOpaqueCube();
	}
	
	//Stop damage being done to entities when scoping etc.
	@Override
	public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity)
	{
		return type.secondaryFunction != EnumSecondaryFunction.MELEE;
	}

	@Override
	public boolean isFull3D()
	{
		return true;
	}
	
	@Override
	public boolean onEntitySwing(EntityLivingBase entityLiving, ItemStack stack)
	{
		if (type.meleeSound != null)
			PacketPlaySound.sendSoundPacket(entityLiving.posX, entityLiving.posY, entityLiving.posZ, Config.soundRange, entityLiving.dimension, type.meleeSound, true);
		//Do custom melee code here
		if(type.secondaryFunction == EnumSecondaryFunction.CUSTOM_MELEE)
		{
			//Do animation
			if(entityLiving.worldObj.isRemote)
			{
				GunAnimations animations = FlansModClient.getGunAnimations(entityLiving, FlansUtils.getSideWithItem(entityLiving, stack));
				animations.doMelee(type.meleeTime);
			}
			//Do custom melee hit detection
			if(entityLiving instanceof EntityPlayer)
			{
				PlayerData data = PlayerHandler.getPlayerData((EntityPlayer)entityLiving);
				data.doMelee((EntityPlayer)entityLiving, type.meleeTime, type);
			}
		}
		return type.secondaryFunction != EnumSecondaryFunction.MELEE;
	}
	
	@Override
    public boolean onBlockStartBreak(ItemStack itemstack, BlockPos pos, EntityPlayer player)
    {
        return true;
    }

	@Override
    public boolean canHarvestBlock(IBlockState state)
    {
        return false;
    }
    
	//TODO @Override
	@SideOnly(Side.CLIENT)
	public int getColorFromItemStack(ItemStack par1ItemStack, int par2)
	{
		return type.colour;
	}

	public boolean isItemStackDamageable()
	{
		return true;
	}
	
	// ----------------- Paintjobs -----------------
	
    @Override
    public void getSubItems(Item item, CreativeTabs tabs, List list){
    	GunType type = ((ItemGun)item).type;
    	if(Config.addAllPaintjobsToCreative){
    		for(Paintjob paintjob : type.paintjobs){
    			addPaintjobToList(item, type, paintjob, list);
    		}
    	}
        else addPaintjobToList(item, type, type.defaultPaintjob, list);
    }
    
    private void addPaintjobToList(Item item, GunType type, Paintjob paintjob, List list)
    {
    	ItemStack gunStack = new ItemStack(item, 1, paintjob.ID);
    	NBTTagCompound tags = new NBTTagCompound();
    	
    	/*
    	NBTTagCompound customPaintTags = new NBTTagCompound();
    	
    	customPaintTags.setInteger("Hash", type.hashCode());
    	customPaintTags.setByteArray("Skin", new byte[] { (byte) 0x40, (byte) 0x00, (byte) 0xff, (byte) 0xff, (byte) 0x61, (byte) 0x74 });
    	customPaintTags.setInteger("SkinWidth", 2);
    	customPaintTags.setInteger("SkinHeight", 1);
    	
    	customPaintTags.setByteArray("Icon", new byte[] { (byte) 0xff, (byte) 0x00, (byte) 0xff });
    	customPaintTags.setInteger("IconWidth", 1);
    	customPaintTags.setInteger("IconHeight", 1);
    	
    	tags.setTag("CustomPaint", customPaintTags);
    	*/
    	
    	gunStack.setTagCompound(tags);
        list.add(gunStack);
    }
    
    // ---------------------------------------------
	
    @Override
    public int getMaxItemUseDuration(ItemStack par1ItemStack)
    {
        return 100;
    }


    @Override
    public EnumAction getItemUseAction(ItemStack par1ItemStack)
    {
        return EnumAction.BOW;
    }


	@Override @Nonnull
	public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack)
	{
		if (slot.getSlotType() != EntityEquipmentSlot.Type.HAND)
		{
			//guns can't be worn as armor
			return super.getAttributeModifiers(slot, stack);
		}
		Multimap<String, AttributeModifier> map = super.getAttributeModifiers(slot, stack);
		if(type.knockbackModifier != 0F)
			map.put(SharedMonsterAttributes.KNOCKBACK_RESISTANCE.getAttributeUnlocalizedName(), new AttributeModifier("KnockbackResist", type.knockbackModifier, 0));
		if(type.moveSpeedModifier != 1F)
			map.put(SharedMonsterAttributes.MOVEMENT_SPEED.getAttributeUnlocalizedName(), new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "MovementSpeed", type.moveSpeedModifier - 1F, 2));
		if(type.secondaryFunction == EnumSecondaryFunction.MELEE)
			map.put(SharedMonsterAttributes.ATTACK_DAMAGE.getAttributeUnlocalizedName(), new AttributeModifier("Weapon modifier", type.meleeDamage, 0));
		return map;
	}



	
	// For when we have custom paintjob names
	//@Override
    //public String getUnlocalizedName(ItemStack stack)
    //{
    //    return getUnlocalizedName();//stack.getTagCompound().getString("Paint");
    //}
	
	@Override
    public boolean canItemEditBlocks()
    {
        return false;
    }

    @Override
	public String getName(){
		return type.shortName;
	}
}
