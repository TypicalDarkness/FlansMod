package com.flansmod.client.gui.config;

import com.flansmod.common.FlansMod;
import com.flansmod.common.util.Config;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.config.GuiConfig;

public class ModGuiConfig extends GuiConfig {
	public ModGuiConfig(GuiScreen parent) {
		super(parent, new ConfigElement(Config.getInstance().getCategory(Configuration.CATEGORY_GENERAL)).getChildElements(),
				FlansMod.MODID, false, false, GuiConfig.getAbridgedConfigPath(Config.getInstance().toString()));
	}
}
