package thut.crafts.entity;

import javax.annotation.Nullable;
import javax.vecmath.Vector3f;

import io.netty.buffer.Unpooled;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.util.math.Vec3d;
import thut.api.entity.blockentity.BlockEntityUpdater;
import thut.api.entity.blockentity.IBlockEntity;
import thut.crafts.entity.EntityCraft.Seat;
import thut.crafts.network.PacketPipeline;

public class CraftInteractHandler
{
    final EntityCraft craft;

    public CraftInteractHandler(EntityCraft lift)
    {
        this.craft = lift;
    }

    public EnumActionResult applyPlayerInteraction(EntityPlayer player, Vec3d vec, @Nullable ItemStack stack,
            EnumHand hand)
    {
        if (player.isSneaking()) return EnumActionResult.PASS;
        vec = vec.addVector(vec.xCoord > 0 ? -0.01 : 0.01, vec.yCoord > 0 ? -0.01 : 0.01,
                vec.zCoord > 0 ? -0.01 : 0.01);
        Vec3d playerPos = player.getPositionVector().addVector(0, player.getEyeHeight(), 0);
        Vec3d start = playerPos.subtract(craft.getPositionVector());
        RayTraceResult trace = IBlockEntity.BlockEntityFormer.rayTraceInternal(start.add(craft.getPositionVector()),
                vec.add(craft.getPositionVector()), craft);
        BlockPos pos;
        float hitX, hitY, hitZ;
        EnumFacing side = EnumFacing.DOWN;
        if (trace == null)
        {
            pos = new BlockPos(0, 0, 0);
            hitX = hitY = hitZ = 0;
        }
        else
        {
            pos = trace.getBlockPos();
            hitX = (float) (trace.hitVec.xCoord - pos.getX());
            hitY = (float) (trace.hitVec.yCoord - pos.getY());
            hitZ = (float) (trace.hitVec.zCoord - pos.getZ());
            side = trace.sideHit;
        }
        IBlockState state = craft.getFakeWorld().getBlockState(pos);
        TileEntity tile = craft.getFakeWorld().getTileEntity(pos);
        boolean blacklist = tile != null && !BlockEntityUpdater.isWhitelisted(tile);
        boolean activate = blacklist || state.getBlock().onBlockActivated(craft.getFakeWorld(), pos, state, player,
                hand, stack, side, hitX, hitY, hitZ);
        if (activate) return EnumActionResult.SUCCESS;
        else if (trace == null || !state.getMaterial().isSolid())
        {
            Vec3d playerLook = playerPos.add(player.getLookVec().scale(4));
            RayTraceResult result = craft.worldObj.rayTraceBlocks(playerPos, playerLook, false, true, false);
            if (result != null && result.typeOfHit == Type.BLOCK)
            {
                pos = result.getBlockPos();
                state = craft.worldObj.getBlockState(pos);
                hitX = (float) (result.hitVec.xCoord - pos.getX());
                hitY = (float) (result.hitVec.yCoord - pos.getY());
                hitZ = (float) (result.hitVec.zCoord - pos.getZ());
                activate = state.getBlock().onBlockActivated(craft.getEntityWorld(), pos, state, player, hand, stack,
                        result.sideHit, hitX, hitY, hitZ);
                if (activate && craft.worldObj.isRemote)
                {
                    PacketBuffer buffer = new PacketBuffer(Unpooled.buffer(25));
                    buffer.writeFloat(hitX);
                    buffer.writeFloat(hitY);
                    buffer.writeFloat(hitZ);
                    buffer.writeByte(result.sideHit.ordinal());
                    buffer.writeBlockPos(pos);
                    PacketPipeline.sendToServer(new PacketPipeline.ServerPacket(buffer));
                    return EnumActionResult.SUCCESS;
                }
            }
            return EnumActionResult.PASS;
        }
        else if (state.getBlock() instanceof BlockStairs)
        {
            if (craft.getSeatCount() == 0)
            {
                MutableBlockPos pos1 = new MutableBlockPos();
                int xMin = craft.getMin().getX();
                int zMin = craft.getMin().getZ();
                int yMin = craft.getMin().getY();
                int sizeX = craft.getTiles().length;
                int sizeY = craft.getTiles()[0].length;
                int sizeZ = craft.getTiles()[0][0].length;
                for (int i = 0; i < sizeX; i++)
                    for (int j = 0; j < sizeY; j++)
                        for (int k = 0; k < sizeZ; k++)
                        {
                            pos1.setPos(i + xMin + craft.posX, j + yMin + craft.posY, k + zMin + craft.posZ);
                            IBlockState state1 = craft.getFakeWorld().getBlockState(pos1);
                            if (state1.getBlock() instanceof BlockStairs)
                            {
                                Vector3f seat = new Vector3f(i + xMin, j + yMin + 0.5f, k + zMin);
                                craft.addSeat(seat);
                            }
                        }
            }

            pos = new BlockPos(craft.getPositionVector());
            pos = trace.getBlockPos().subtract(pos);
            for (int i = 0; i < craft.getSeatCount(); i++)
            {
                Seat seat = craft.getSeat(i);
                Vector3f seatPos = seat.seat;
                BlockPos pos1 = new BlockPos(seatPos.x, seatPos.y, seatPos.z);
                if (pos1.equals(pos))
                {
                    if (!craft.worldObj.isRemote && !seat.entityId.equals(player.getUniqueID()))
                    {
                        craft.setSeatID(i, player.getUniqueID());
                        player.startRiding(craft);
                    }
                    break;
                }
            }
        }
        return EnumActionResult.PASS;
    }

    public boolean processInitialInteract(EntityPlayer player, @Nullable ItemStack stack, EnumHand hand)
    {
        if (stack != null && stack.getItem() == Items.BLAZE_ROD)
        {
            if (stack.getTagCompound() == null)
            {
                craft.setDead();
            }
            return true;
        }
        return false;
    }
}
