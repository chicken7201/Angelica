package com.gtnewhorizons.angelica.common;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.function.Supplier;

public class BlockTesrTranslucentTest extends Block {

    private final Supplier<TileEntity> tile;

    /** Initializes BlockTesrTranslucentTest. */
    public BlockTesrTranslucentTest(Supplier<TileEntity> tile) {
        super(Material.glass);
        this.tile = tile;
        setHardness(0.3f);
        setLightOpacity(0);
    }

    /** Handles is opaque cube. */
    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    /** Handles render as normal block. */
    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    /** Handles get render type. */
    @Override
    public int getRenderType() {
        return -1;
    }

    /** Handles has tile entity. */
    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    /** Handles create tile entity. */
    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return tile.get();
    }
}
