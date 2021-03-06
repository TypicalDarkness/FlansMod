package com.flansmod.common.network.packets;

import com.flansmod.common.driveables.EntityDriveable;

import io.netty.buffer.ByteBuf;
import net.fexcraft.mod.lib.api.network.IPacket;
import net.fexcraft.mod.lib.util.render.RGB;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class PacketDriveableSync implements IPacket, IMessage{
	
	public int entityId;
	public RGB primary;
	public RGB secondary;
	public String texture_url;
	
	public PacketDriveableSync(){}
	
	public PacketDriveableSync(EntityDriveable ent){
		this.entityId = ent.getEntityId();
		this.primary = ent.driveableData.primary_color;
		this.secondary = ent.driveableData.secondary_color;
		this.texture_url = ent.driveableData.texture_url;
		if(texture_url == null){
			texture_url = "";
		}
	}

	@Override
	public void toBytes(ByteBuf bbuf){
		PacketBuffer buf = new PacketBuffer(bbuf);
		buf.writeInt(entityId);
		buf.writeFloat(primary.red);
		buf.writeFloat(primary.green);
		buf.writeFloat(primary.blue);
		buf.writeFloat(secondary.red);
		buf.writeFloat(secondary.green);
		buf.writeFloat(secondary.blue);
		buf.writeString(texture_url);
	}

	@Override
	public void fromBytes(ByteBuf bbuf){
		PacketBuffer buf = new PacketBuffer(bbuf);
		entityId = buf.readInt();
		primary = new RGB(buf.readFloat(), buf.readFloat(), buf.readFloat());
		secondary = new RGB(buf.readFloat(), buf.readFloat(), buf.readFloat());
		texture_url = buf.readString(999);
	}
	
}