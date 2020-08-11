package com.untamedears.realisticbiomes;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import com.untamedears.realisticbiomes.growth.ColumnPlantGrower;
import com.untamedears.realisticbiomes.growth.FruitGrower;
import com.untamedears.realisticbiomes.growthconfig.PlantGrowthConfig;
import com.untamedears.realisticbiomes.model.Plant;
import com.untamedears.realisticbiomes.utils.RBUtils;

import vg.civcraft.mc.civmodcore.api.BlockAPI;

public class PlantLogicManager {

	private PlantManager plantManager;
	private GrowthConfigManager growthConfigManager;
	private Set<Material> fruitBlocks;
	private Set<Material> columnBlocks;

	public PlantLogicManager(PlantManager plantManager, GrowthConfigManager growthConfigManager) {
		this.plantManager = plantManager;
		this.growthConfigManager = growthConfigManager;
		initAdjacentPlantBlocks(growthConfigManager.getAllGrowthConfigs());
	}

	public void handleBlockDestruction(Block block) {
		if (plantManager == null) {
			return;
		}
		Plant plant = plantManager.getPlant(block);
		if (plant != null) {
			plantManager.deletePlant(plant);
			return;
		}
		// column plants will always hold the plant object in the bottom most block, so
		// we need
		// to update that if we just broke one of the upper blocks of a column plant
		if (columnBlocks != null && columnBlocks.contains(block.getType())) {
			Block sourceColumn = ColumnPlantGrower.getRelativeBlock(block, BlockFace.DOWN);
			Plant bottomColumnPlant = plantManager.getPlant(sourceColumn);
			if (bottomColumnPlant != null) {
				bottomColumnPlant.resetCreationTime();
				initGrowthTime(bottomColumnPlant, sourceColumn);
			}
		}
		if (fruitBlocks != null && fruitBlocks.contains(block.getType())) {
			for(BlockFace face : BlockAPI.PLANAR_SIDES) {
				Block possibleStem = block.getRelative(face);
				Plant stem = plantManager.getPlant(possibleStem);
				if (stem == null) {
					continue;
				}
				if (stem.getGrowthConfig() == null || !(stem.getGrowthConfig().getGrower() instanceof FruitGrower)) {
					continue;
				}
				FruitGrower grower = (FruitGrower) stem.getGrowthConfig().getGrower();
				if (grower.getFruitMaterial() != block.getType()) {
					continue;
				}
				if (grower.getStage(stem) != grower.getMaxStage()) {
					continue;
				}
				if (grower.getTurnedDirection(possibleStem) == face.getOppositeFace()) {
					stem.resetCreationTime();
					initGrowthTime(stem, possibleStem);
					grower.setStage(stem, 0);
				}
			}
		}
	}

	private void initAdjacentPlantBlocks(Set<PlantGrowthConfig> growthConfigs) {
		for (PlantGrowthConfig config : growthConfigs) {
			if (config.getGrower() instanceof FruitGrower) {
				FruitGrower grower = (FruitGrower) config.getGrower();
				if (fruitBlocks == null) {
					fruitBlocks = new HashSet<>();
				}
				fruitBlocks.add(grower.getFruitMaterial());
				continue;
			}
			if (config.getGrower() instanceof ColumnPlantGrower) {
				ColumnPlantGrower grower = (ColumnPlantGrower) config.getGrower();
				if (columnBlocks == null) {
					columnBlocks = new HashSet<>();
				}
				columnBlocks.add(grower.getMaterial());
			}
		}
	}

	public void handlePlantCreation(Block block, ItemStack itemUsed) {
		if (plantManager == null) {
			return;
		}
		PlantGrowthConfig growthConfig = growthConfigManager.getGrowthConfigByItem(itemUsed);
		if (growthConfig == null || !growthConfig.isPersistent()) {
			return;
		}
		Plant plant = new Plant(block.getLocation(), growthConfig);
		plantManager.putPlant(plant);
		initGrowthTime(plant, block);
	}

	public void initGrowthTime(Plant plant, Block block) {
		PlantGrowthConfig growthConfig = plant.getGrowthConfig();
		if (growthConfig == null) {
			growthConfig = growthConfigManager.getGrowthConfigFallback(block.getType());
			if (growthConfig == null) {
				plantManager.deletePlant(plant);
				return;
			}
			plant.setGrowthConfig(growthConfig);
		}
		if (!growthConfig.isPersistent()) {
			return;
		}
		long nextUpdateTime = growthConfig.updatePlant(plant, block);
		if (nextUpdateTime != -1) {
			plant.setNextGrowthTime(nextUpdateTime);
		}
	}

}
