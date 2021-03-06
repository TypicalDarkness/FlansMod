package com.flansmod.client;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.flansmod.client.debug.EntityDebugAABB;
import com.flansmod.client.debug.EntityDebugDot;
import com.flansmod.client.debug.EntityDebugVector;
import com.flansmod.client.debug.RenderDebugAABB;
import com.flansmod.client.debug.RenderDebugDot;
import com.flansmod.client.debug.RenderDebugVector;
import com.flansmod.client.gui.GuiDriveableMenu;
import com.flansmod.client.model.OverrideVanillaModelLoader;
import com.flansmod.client.model.RenderAAGun;
import com.flansmod.client.model.RenderBullet;
import com.flansmod.client.model.RenderCustomEntityItem;
import com.flansmod.client.model.RenderCustomItem;
import com.flansmod.client.model.RenderGrenade;
import com.flansmod.client.model.RenderGun;
import com.flansmod.client.model.RenderItemHolder;
import com.flansmod.client.model.RenderMG;
import com.flansmod.client.model.RenderMecha;
import com.flansmod.client.model.RenderNull;
import com.flansmod.client.model.RenderParachute;
import com.flansmod.client.model.RenderPlane;
import com.flansmod.client.model.RenderVehicle;
import com.flansmod.common.CommonProxy;
import com.flansmod.common.EntityCustomItem;
import com.flansmod.common.FlansMod;
import com.flansmod.common.PlayerData;
import com.flansmod.common.PlayerHandler;
import com.flansmod.common.TileEntityItemHolder;
import com.flansmod.common.driveables.DriveablePart;
import com.flansmod.common.driveables.DriveableType;
import com.flansmod.common.driveables.EntityDriveable;
import com.flansmod.common.driveables.EntityPlane;
import com.flansmod.common.driveables.EntitySeat;
import com.flansmod.common.driveables.EntityVehicle;
import com.flansmod.common.driveables.EntityWheel;
import com.flansmod.common.driveables.PlaneType;
import com.flansmod.common.driveables.mechas.EntityMecha;
import com.flansmod.common.guns.EntityAAGun;
import com.flansmod.common.guns.EntityBullet;
import com.flansmod.common.guns.EntityGrenade;
import com.flansmod.common.guns.EntityMG;
import com.flansmod.common.guns.boxes.GunBoxType;
import com.flansmod.common.teams.ArmourBoxType;
import com.flansmod.common.teams.TileEntitySpawner;
import com.flansmod.common.tools.EntityParachute;
import com.flansmod.common.types.IFlanItem;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.util.Util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.FMLModContainer;
import net.minecraftforge.fml.common.MetadataCollection;
import net.minecraftforge.fml.common.discovery.ContainerType;
import net.minecraftforge.fml.common.discovery.ModCandidate;

public class ClientProxy extends CommonProxy
{
	public static String modelDir = "com.flansmod.client.model.";
	
	/* These renderers handle rendering in hand items */
	public static RenderGun gunRenderer;
	public static RenderGrenade grenadeRenderer;
	public static RenderPlane planeRenderer;
	public static RenderVehicle vehicleRenderer;
	public static RenderMecha mechaRenderer;




	/** The file locations of the content packs, used for loading */
	public List<File> contentPacks;
	
	private FlansModClient flansModClient;

	@Override
	public void load()
	{
		flansModClient = new FlansModClient();
		flansModClient.load();

        /*
		//Register custom modelLoader for PaintableTypes
		for(InfoType type : InfoType.infoTypes.values())
		{
			if(type != null && type.item != null && type.getModel() != null)
			{
				if(type instanceof PaintableType)
				{
					for(Paintjob paintjob : ((PaintableType) type).paintjobs)
					{
						//The location that is used to register the custom location in ItemUtil.registerRender
						ModelResourceLocation modelLocation = new ModelResourceLocation(type.item.getRegistryName() + "_" +  paintjob.ID, "inventory");
						OverrideVanillaModelLoader.INSTANCE.setCusomIcon(modelLocation, new ResourceLocation(FlansMod.MODID, "items/" + paintjob.iconPath));
					}
				}
				//else Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(type.item, 0, new ModelResourceLocation("flansmod:" + type.shortName, "inventory"));
			}
		}
		*/

		
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(FlansMod.workbench), 0, new ModelResourceLocation("flansmod:flansWorkbench_guns", "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(FlansMod.workbench), 1, new ModelResourceLocation("flansmod:flansWorkbench_vehicles", "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(FlansMod.workbench), 2, new ModelResourceLocation("flansmod:flansWorkbench_parts", "inventory"));
		//ModelBakery.addVariantName(Item.getItemFromBlock(FlansMod.workbench), new String[] {"flansmod:flansWorkbench_guns", "flansmod:flansWorkbench_parts", "flansmod:flansWorkbench_vehicles"});

		/*Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(FlansMod.opStick, 0, new ModelResourceLocation("flansmod:opstick_Ownership", "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(FlansMod.opStick, 1, new ModelResourceLocation("flansmod:opstick_Connecting", "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(FlansMod.opStick, 2, new ModelResourceLocation("flansmod:opstick_Mapping", "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(FlansMod.opStick, 3, new ModelResourceLocation("flansmod:opstick_Destruction", "inventory"));*/
		//ModelBakery.addVariantName(FlansMod.opStick, new String[] {"flansmod:opstick_Ownership", "flansmod:opstick_Connecting", "flansmod:opstick_Mapping", "flansmod:opstick_Destruction"});
		
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(FlansMod.spawner), 0, new ModelResourceLocation("flansmod:teamsSpawner_items", "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(FlansMod.spawner), 1, new ModelResourceLocation("flansmod:teamsSpawner_players", "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(FlansMod.spawner), 2, new ModelResourceLocation("flansmod:teamsSpawner_vehicles", "inventory"));
		//ModelBakery.addVariantName(Item.getItemFromBlock(FlansMod.spawner), new String[] {"flansmod:teamsSpawner_items", "flansmod:teamsSpawner_players", "flansmod:teamsSpawner_vehicles"});

		//Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(FlansMod.flag, 0, new ModelResourceLocation("flansmod:flagpole", "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(FlansMod.rainbowPaintcan, 0, new ModelResourceLocation("flansmod:rainbowPaintcan", "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(FlansMod.paintjobTable), 0, new ModelResourceLocation("flansmod:paintjobTable", "inventory"));
		//ModelBakery.addVariantName(Item.getItemFromBlock(FlansMod.paintjobTable), new String[] {"flansmod:paintjobTable"});
		
		gunRenderer = new RenderGun(Minecraft.getMinecraft().getRenderManager());
		grenadeRenderer = new RenderGrenade(Minecraft.getMinecraft().getRenderManager());
		planeRenderer = new RenderPlane(Minecraft.getMinecraft().getRenderManager());
		vehicleRenderer = new RenderVehicle(Minecraft.getMinecraft().getRenderManager());
		mechaRenderer = new RenderMecha(Minecraft.getMinecraft().getRenderManager());
		
		//Register custom item renderers
		/*for(GunType gunType : GunType.guns.values())
			MinecraftForgeClient.registerItemRenderer(gunType.item, gunRenderer);*/
		/*for(GrenadeType grenadeType : GrenadeType.grenades)
			MinecraftForgeClient.registerItemRenderer(grenadeType.item, grenadeRenderer);*/	
		/*for(PlaneType planeType : PlaneType.types)
			MinecraftForgeClient.registerItemRenderer(planeType.item, planeRenderer);
		for(VehicleType vehicleType : VehicleType.types)
			MinecraftForgeClient.registerItemRenderer(vehicleType.item, vehicleRenderer);
		for(MechaType mechaType : MechaType.types)
			MinecraftForgeClient.registerItemRenderer(mechaType.item, mechaRenderer);*/


		ModelLoaderRegistry.registerLoader(OverrideVanillaModelLoader.INSTANCE);

        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityItemHolder.class, new RenderItemHolder());

        // Create one event handler for the client and register it with MC Forge and FML
		ClientEventHandler eventHandler = new ClientEventHandler();
		//FMLCommonHandler.instance().bus().register(eventHandler);
		MinecraftForge.EVENT_BUS.register(eventHandler);
	}
		
	/** This method reloads all textures from all mods and resource packs. It forces Minecraft to read images from the content packs added after mod init */
	@Override
	public void forceReload()
	{
		Minecraft.getMinecraft().refreshResources();
	}

	/** This method grabs all the content packs and puts them in a list. The client side part registers them as FMLModContainers which adds their resources to the game after a refresh */
	@Override
	public List<File> getContentList(Method method, ClassLoader classloader)
	{
		contentPacks = new ArrayList<File>();
		for (File file : FlansMod.flanDir.listFiles())
		{
			if (file.isDirectory() || zipJar.matcher(file.getName()).matches())
			{
				try
				{
					method.invoke(classloader, file.toURI().toURL());
					
					HashMap<String, Object> map = new HashMap<String, Object>();
					map.put("modid", "ffmm");
					map.put("name", "Flan's Mod Minus : " + file.getName());
					map.put("version", "1");
					FMLModContainer container = new FMLModContainer("com.flansmod.common.FlansMod", new ModCandidate(file, file, file.isDirectory() ? ContainerType.DIR : ContainerType.JAR), map);
					container.bindMetadata(MetadataCollection.from(null, ""));
					FMLClientHandler.instance().addModAsResource(container);
				
				} catch (Exception e)
				{
					Util.log("Failed to load images for content pack : " + file.getName());
					e.printStackTrace();
				}
				// Add the directory to the content pack list
				Util.log("Loaded content pack : " + file.getName());
				contentPacks.add(file);
			}
		}
			
		Util.log("Loaded textures and models.");
		return contentPacks;
	}
	
	/** Register entity renderers */
	@Override
	public void registerRenderers()
	{
		
		RenderingRegistry.registerEntityRenderingHandler(EntityBullet.class, RenderBullet::new);
		RenderingRegistry.registerEntityRenderingHandler(EntityGrenade.class, RenderGrenade::new);
		RenderingRegistry.registerEntityRenderingHandler(EntityPlane.class, RenderPlane::new);
		RenderingRegistry.registerEntityRenderingHandler(EntityVehicle.class, RenderVehicle::new);
		RenderingRegistry.registerEntityRenderingHandler(EntityAAGun.class, RenderAAGun::new);
		RenderingRegistry.registerEntityRenderingHandler(EntitySeat.class, RenderNull::new);		
		RenderingRegistry.registerEntityRenderingHandler(EntityWheel.class, RenderNull::new);
		RenderingRegistry.registerEntityRenderingHandler(EntityMG.class, RenderMG::new);
		RenderingRegistry.registerEntityRenderingHandler(EntityParachute.class, RenderParachute::new);
		RenderingRegistry.registerEntityRenderingHandler(EntityDebugDot.class, RenderDebugDot::new);
		RenderingRegistry.registerEntityRenderingHandler(EntityDebugVector.class, RenderDebugVector::new);
		RenderingRegistry.registerEntityRenderingHandler(EntityDebugAABB.class, RenderDebugAABB::new);
		RenderingRegistry.registerEntityRenderingHandler(EntityMecha.class, RenderMecha::new);
		RenderingRegistry.registerEntityRenderingHandler(EntityCustomItem.class, RenderCustomEntityItem::new);

		ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySpawner.class, new TileEntitySpawnerRenderer());
	}
	
	/** Old one time tutorial code that displays messages the first time you enter a plane / vehicle. Needs reworking */
	@Override
	public void doTutorialStuff(EntityPlayer player, EntityDriveable entityType)
	{
		if (!FlansModClient.doneTutorial)
		{
			FlansModClient.doneTutorial = true;
			
			player.sendMessage(new TextComponentString("Press " + Keyboard.getKeyName(KeyInputHandler.inventoryKey.getKeyCode()) + " to open the menu"));
			player.sendMessage(new TextComponentString("Press " + Keyboard.getKeyName(Minecraft.getMinecraft().gameSettings.keyBindSneak.getKeyCode()) + " to get out"));
			player.sendMessage(new TextComponentString("Press " + Keyboard.getKeyName(KeyInputHandler.controlSwitchKey.getKeyCode()) + " to switch controls"));
			player.sendMessage(new TextComponentString("Press " + Keyboard.getKeyName(KeyInputHandler.modeKey.getKeyCode()) + " to switch VTOL mode"));
			if (entityType instanceof EntityPlane)
			{
				if(PlaneType.getPlane(((EntityPlane)entityType).driveableType).hasGear)
					player.sendMessage(new TextComponentString("Press " + Keyboard.getKeyName(KeyInputHandler.gearKey.getKeyCode()) + " to switch the gear"));
				if(PlaneType.getPlane(((EntityPlane)entityType).driveableType).hasDoor)
					player.sendMessage(new TextComponentString("Press " + Keyboard.getKeyName(KeyInputHandler.doorKey.getKeyCode()) + " to switch the doors"));
				if(PlaneType.getPlane(((EntityPlane)entityType).driveableType).hasWing)
					player.sendMessage(new TextComponentString("Press " + Keyboard.getKeyName(KeyInputHandler.modeKey.getKeyCode()) + " to switch the wings"));
			}
		}
	}

	public static RenderCustomItem getRenderer(IFlanItem item)
	{
		return item.getRenderItemEntity();
	}

	/** Adds the client side text message regarding mouse control mode switching */
	@Override
	public void changeControlMode(EntityPlayer player){
		if(FlansModClient.flipControlMode()){
			player.sendMessage(new TextComponentString("Mouse Control mode is now set to " + FlansModClient.controlModeMouse));
		}
	}
	
	/** Whether the player is in mouse control mode for planes. Now the default setting for planes, but it can be deactivated to look around while flying */
	@Override
	public boolean mouseControlEnabled()
	{
		return FlansModClient.controlModeMouse;
	}
	
	/** Called when the player presses the plane inventory key. Opens menu client side */
	@Override
	public void openDriveableMenu(EntityPlayer player, World world, EntityDriveable driveable)
	{
		FMLClientHandler.instance().getClient().displayGuiScreen(new GuiDriveableMenu(player.inventory, world, driveable));
	}
	
	/** Helper method that sorts out packages with model name input
	 * For example, the model class "com.flansmod.client.model.mw.ModelMP5"
	 * is referenced in the type file by the string "mw.MP5" */
	private String getModelName(String in)
	{
		//Split about dots
		String[] split = in.split("\\.");
		//If there is no dot, our model class is in the default model package
		if(split.length == 1)
			return "Model" + in;
		//Otherwise, we need to slightly rearrange the wording of the string for it to make sense
		else if(split.length > 1)
		{
			String out = "Model" + split[split.length - 1];
			for(int i = split.length - 2; i >= 0; i--)
			{
				out = split[i] + "." + out;
			}
			return out;
		}
		return in;
	}
	
	/** Generic model loader method for getting model classes and casting them to the required class type */
	@Override
	public <T> T loadModel(String s, String shortName, Class<T> typeClass)
	{
		if(s == null || shortName == null)
			return null;
		try 
		{	
			return typeClass.cast(Class.forName(modelDir + getModelName(s)).getConstructor().newInstance());
		}
		catch(Exception e)
		{
			Util.log("Failed to load model : " + shortName + " (" + s + ")");
			e.printStackTrace();
		}
		return null;
	}
	
	/** Sound loading method. Defers to FlansModResourceHandler */
	@Override
	public void loadSound(String contentPack, String type, String sound)
	{
		FlansModResourceHandler.getSound(sound);
		//FMLClientHandler.instance().getClient().installResource("sound3/" + type + "/" + sound + ".ogg", new File(FMLClientHandler.instance().getClient().mcDataDir, "/Flan/" + contentPack + "/sounds/" + sound + ".ogg"));
	}
	
	/** Checks whether "player" is the current player. Always false on server, since there is no current player */
	@Override
	public boolean isThePlayer(EntityPlayer player)
	{
		return player == FMLClientHandler.instance().getClient().player;
	}
	
	/* Gun and armour box crafting methods */
	@Override
	public void buyGun(GunBoxType type, InfoType gun)
	{
		//TODO FlansMod.getPacketHandler().sendToServer(new PacketBuyWeapon(type, gun));
		PlayerData data = PlayerHandler.getPlayerData(Minecraft.getMinecraft().player);
		data.shootTimeLeft = data.shootTimeRight = 10;
	}
	
	@Override
	public void buyArmour(String shortName, int piece, ArmourBoxType box)
	{
		//TODO FlansMod.getPacketHandler().sendToServer(new PacketBuyArmour(box.shortName, shortName, piece));
		PlayerData data = PlayerHandler.getPlayerData(Minecraft.getMinecraft().player);
		data.shootTimeLeft = data.shootTimeRight = 10;
	}
	
	@Override
	public void craftDriveable(EntityPlayer player, DriveableType type)
	{
		//Craft it this side (so the inventory updates immediately) and then send a packet to the server so that it is crafted that side too
		super.craftDriveable(player, type);
		if(player.world.isRemote){
			//TODO FlansMod.getPacketHandler().sendToServer(new PacketCraftDriveable(type.shortName));
		}
	}
	
	@Override
	public void repairDriveable(EntityPlayer driver, EntityDriveable driving, DriveablePart part)
	{
		//Repair it this side (so the inventory updates immediately) and then send a packet to the server so that it is repaired that side too
		super.repairDriveable(driver, driving, part);
		if(driver.world.isRemote){
			//TODO FlansMod.getPacketHandler().sendToServer(new PacketRepairDriveable(part.type));
		}
	}
	
	/** Helper method that returns whether there is a GUI open */
	@Override
	public boolean isScreenOpen()
	{
		return Minecraft.getMinecraft().currentScreen != null;
	}
	
	/** Mecha input getters */
	@Override
	public boolean isKeyDown(int key)
	{
		switch(key)
		{
		case 0 : //Press Forwards
			return keyDown(Minecraft.getMinecraft().gameSettings.keyBindForward.getKeyCode());
			
		case 1 : //Press Backwards
			return keyDown(Minecraft.getMinecraft().gameSettings.keyBindBack.getKeyCode()); 
			
		case 2 : //Press Left
			return keyDown(Minecraft.getMinecraft().gameSettings.keyBindLeft.getKeyCode());
			
		case 3 : //Press Right
			return keyDown(Minecraft.getMinecraft().gameSettings.keyBindRight.getKeyCode()); 

		case 4 : //Press Jump
			return keyDown(Minecraft.getMinecraft().gameSettings.keyBindJump.getKeyCode());
		}
		return false;
	}
	
	/** Helper method that deals with the way Minecraft handles binding keys to the mouse */
	@Override
	public boolean keyDown(int keyCode)
	{
	   	boolean state = (keyCode < 0 ? Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode));
		return state;
	}
	//No longer needed, CustomModelLoaders will be used to pretend to load the missing files.
	/*
	@Override
	public void addMissingJSONs(HashMap<Integer, InfoType> types)
	{
		for(InfoType type : types.values())
		{
			try
			{
				EnumType typeToCheckFor = EnumType.getFromObject(type);
				File contentPackDir = new File(FlansMod.flanDir, type.contentPack);
				if(contentPackDir.isDirectory())
				{
					File itemModelsDir = new File(contentPackDir, "/assets/flansmod/models/item");
					if(!itemModelsDir.exists())
						itemModelsDir.mkdirs();
					File blockModelsDir = new File(contentPackDir, "/assets/flansmod/models/block");
					if(!blockModelsDir.exists())
						blockModelsDir.mkdirs();
					File blockstatesDir = new File(contentPackDir, "/assets/flansmod/blockstates");
					if(!blockstatesDir.exists())
						blockstatesDir.mkdirs();
					
					//Do block json for boxes
					if(typeToCheckFor == EnumType.armourBox || typeToCheckFor == EnumType.box)
					{
						BoxType box = (BoxType)type;
						
						createJSONFile(new File(itemModelsDir, type.shortName + ".json"), "{ \"parent\": \"flansmod:block/" + type.shortName + "\", \"display\": { \"thirdperson\": { \"rotation\": [ 10, -45, 170 ], \"translation\": [ 0, 1.5, -2.75 ], \"scale\": [ 0.375, 0.375, 0.375 ] } } }");
						createJSONFile(new File(blockModelsDir, type.shortName + ".json"), "{ \"parent\": \"block/cube\", \"textures\": { \"particle\": \"flansmod:blocks/" + box.sideTexturePath + 
								"\", \"down\": \"flansmod:blocks/" + box.bottomTexturePath + "\", \"up\": \"flansmod:blocks/" + box.topTexturePath + "\", \"north\": \"flansmod:blocks/" + box.sideTexturePath + 
								"\", \"east\": \"flansmod:blocks/" + box.sideTexturePath + "\", \"south\": \"flansmod:blocks/" + box.sideTexturePath + "\", \"west\": \"flansmod:blocks/" + box.sideTexturePath + "\" } } ");
						createJSONFile(new File(blockstatesDir, type.shortName + ".json"), "{ \"variants\": { \"normal\": { \"model\": \"flansmod:" + type.shortName + "\" } } }");
					}
					else if(type instanceof PaintableType)
					{
						for(Paintjob paintjob : ((PaintableType)type).paintjobs)
						{
							createJSONFile(new File(itemModelsDir, type.shortName + (paintjob.name.equals("") ? "" : ("_" + paintjob.name)) + ".json"), "{ \"parent\": \"builtin/generated\", \"textures\": { \"layer0\": \"flansmod:items/" + type.iconPath + (paintjob.name.equals("") ? "" : ("_" + paintjob.name)) + "\" }, \"display\": { \"thirdperson\": { \"rotation\": [ 0, 90, -45 ], \"translation\": [ 0, 2, -2 ], \"scale\": [ 0, 0, 0 ] }, \"firstperson\": { \"rotation\": [ 0, -135, 25 ], \"translation\": [ 0, 4, 2 ], \"scale\": [ 1.7, 1.7, 1.7 ] } } }");
						}
					}
					else if(typeToCheckFor == EnumType.itemHolder)
					{
						createJSONFile(new File(blockstatesDir, type.shortName + ".json"), 
					"{ \"variants\": { \"facing=north\": { \"model\": \"flansmod:" + type.shortName + 
								"\" }, \"facing=east\": { \"model\": \"flansmod:" + type.shortName + 
								"\" }, \"facing=south\": { \"model\": \"flansmod:" + type.shortName + 
								"\" }, \"facing=west\": { \"model\": \"flansmod:" + type.shortName + "\" } } }");	
						createJSONFile(new File(blockModelsDir, type.shortName + ".json"), "{ \"ambientocclusion\": false, \"textures\": { \"particle\": \"flansmod:items/" + type.iconPath + "\" }, \"elements\": [ {\"from\": [ 0, 0, 0 ],\"to\": [ 0, 0, 0 ], \"faces\": { \"down\":  { \"texture\": \"#down\", \"cullface\": \"down\" }, \"up\":    { \"texture\": \"#up\", \"cullface\": \"up\" }, \"north\": { \"texture\": \"#north\", \"cullface\": \"north\" }, \"south\": { \"texture\": \"#south\", \"cullface\": \"south\" }, \"west\":  { \"texture\": \"#west\", \"cullface\": \"west\" }, \"east\":  { \"texture\": \"#east\", \"cullface\": \"east\" } } } ] }");
						createJSONFile(new File(itemModelsDir, type.shortName + ".json"), "{ \"parent\": \"builtin/generated\", \"textures\": { \"layer0\": \"flansmod:items/" + type.iconPath + "\" }, \"display\": { \"thirdperson\": { \"rotation\": [ -90, 0, 0 ], \"translation\": [ 0, 1, -3 ], \"scale\": [ 0.55, 0.55, 0.55 ] }, \"firstperson\": { \"rotation\": [ 0, -135, 25 ], \"translation\": [ 0, 4, 2 ], \"scale\": [ 1.7, 1.7, 1.7 ] } } }");						
					}
					//Create the item JSON for normal items
					else if(typeToCheckFor != EnumType.team && typeToCheckFor != EnumType.playerClass)
					{
						createJSONFile(new File(itemModelsDir, type.shortName + ".json"), "{ \"parent\": \"builtin/generated\", \"textures\": { \"layer0\": \"flansmod:items/" + type.iconPath + "\" }, \"display\": { \"thirdperson\": { \"rotation\": [ -90, 0, 0 ], \"translation\": [ 0, 1, -3 ], \"scale\": [ 0.55, 0.55, 0.55 ] }, \"firstperson\": { \"rotation\": [ 0, -135, 25 ], \"translation\": [ 0, 4, 2 ], \"scale\": [ 1.7, 1.7, 1.7 ] } } }");
					}
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	private void createJSONFile(File file, String contents) throws Exception
	{
		if(!file.exists())
		{
			file.createNewFile();
			BufferedWriter out = new BufferedWriter(new FileWriter(file));
			out.write(contents);
			out.close();
		}
	}
	*/
}
