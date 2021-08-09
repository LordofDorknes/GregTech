package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

public class SteamMultiblockRecipeLogic extends AbstractRecipeLogic {

    private IMultipleTankHandler steamFluidTank;
    private IFluidTank steamFluidTankCombined;

    // EU per mB
    private final double conversionRate;

    public SteamMultiblockRecipeLogic(RecipeMapSteamMultiblockController tileEntity, RecipeMap<?> recipeMap, IMultipleTankHandler steamFluidTank, double conversionRate) {
        super(tileEntity, recipeMap);
        this.steamFluidTank = steamFluidTank;
        this.conversionRate = conversionRate;
        allowOverclocking = false;
        combineSteamTanks();
    }

    public IFluidTank getSteamFluidTankCombined() {
        combineSteamTanks();
        return steamFluidTankCombined;
    }

    @Override
    protected IItemHandlerModifiable getInputInventory() {
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        return controller.getInputInventory();
    }

    @Override
    protected IItemHandlerModifiable getOutputInventory() {
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        return controller.getOutputInventory();
    }

    protected IMultipleTankHandler getSteamFluidTank() {
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        return controller.steamFluidTank;
    }

    private void combineSteamTanks() {
        steamFluidTank = getSteamFluidTank();
        if (steamFluidTank == null)
            steamFluidTankCombined = new FluidTank(0);
        else {
            int capacity = steamFluidTank.getTanks() * 64000;
            steamFluidTankCombined = new FluidTank(capacity);
            steamFluidTankCombined.fill(steamFluidTank.drain(capacity, false), true);
        }
    }

    @Override
    public void update() {

        // Fixes an annoying GTCE bug in AbstractRecipeLogic
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        if (isActive && !controller.isStructureFormed()) {
            progressTime = 0;
            wasActiveAndNeedsUpdate = true;
        }

        combineSteamTanks();
        super.update();
    }

    @Override
    protected long getEnergyStored() {
        combineSteamTanks();
        return (long) Math.ceil(steamFluidTankCombined.getFluidAmount() * conversionRate);
    }

    @Override
    protected long getEnergyCapacity() {
        combineSteamTanks();
        return (long) Math.floor(steamFluidTankCombined.getCapacity() * conversionRate);
    }

    @Override
    protected boolean drawEnergy(int recipeEUt) {
        combineSteamTanks();
        int resultDraw = (int) Math.ceil(recipeEUt / conversionRate);
        return resultDraw >= 0 && steamFluidTankCombined.getFluidAmount() >= resultDraw &&
                steamFluidTank.drain(resultDraw, true) != null;
    }

    @Override
    protected long getMaxVoltage() {
        return GTValues.V[GTValues.LV];
    }

    @Override
    protected boolean setupAndConsumeRecipeInputs(Recipe recipe) {
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        if (controller.checkRecipe(recipe, false) &&
                super.setupAndConsumeRecipeInputs(recipe)) {
            controller.checkRecipe(recipe, true);
            return true;
        } else return false;
    }

    // Do this to casually ignore fluids from steam multiblocks
    @Override
    protected boolean checkRecipeInputsDirty(IItemHandler inputs, IMultipleTankHandler fluidInputs) {
        return super.checkRecipeInputsDirty(inputs, new FluidTankList(false));
    }

    @Override
    protected void completeRecipe() {
        super.completeRecipe();
        ventSteam();
    }

    private void ventSteam() {
        BlockPos machinePos = metaTileEntity.getPos();
        EnumFacing ventingSide = metaTileEntity.getFrontFacing();
        BlockPos ventingBlockPos = machinePos.offset(ventingSide);
        IBlockState blockOnPos = metaTileEntity.getWorld().getBlockState(ventingBlockPos);
        if (blockOnPos.getCollisionBoundingBox(metaTileEntity.getWorld(), ventingBlockPos) == Block.NULL_AABB) {
            WorldServer world = (WorldServer) metaTileEntity.getWorld();
            double posX = machinePos.getX() + 0.5 + ventingSide.getXOffset() * 0.6;
            double posY = machinePos.getY() + 0.5 + ventingSide.getYOffset() * 0.6;
            double posZ = machinePos.getZ() + 0.5 + ventingSide.getZOffset() * 0.6;

            world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, posX, posY, posZ,
                    7 + world.rand.nextInt(3),
                    ventingSide.getXOffset() / 2.0,
                    ventingSide.getYOffset() / 2.0,
                    ventingSide.getZOffset() / 2.0, 0.1);
            world.playSound(null, posX, posY, posZ, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
    }
}