package thut.crafts.entity;

import javax.annotation.Nullable;
import javax.vecmath.Vector3f;

import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import thut.api.entity.IMultiplePassengerEntity.Seat;
import thut.api.entity.blockentity.BlockEntityInteractHandler;
import thut.api.entity.blockentity.IBlockEntity;

public class CraftInteractHandler extends BlockEntityInteractHandler
{
    final EntityCraft craft;

    public CraftInteractHandler(EntityCraft lift)
    {
        super(lift);
        this.craft = lift;
    }

    @Override
    public EnumActionResult applyPlayerInteraction(EntityPlayer player, Vec3d vec, ItemStack stack, EnumHand hand)
    {
        if (player.isSneaking()) return EnumActionResult.PASS;
        EnumActionResult result = super.applyPlayerInteraction(player, vec, stack, hand);
        if (result == EnumActionResult.SUCCESS || processInitialInteract(player, player.getHeldItem(hand), hand))
            return EnumActionResult.SUCCESS;
        vec = vec.addVector(vec.x > 0 ? -0.01 : 0.01, vec.y > 0 ? -0.01 : 0.01, vec.z > 0 ? -0.01 : 0.01);
        Vec3d playerPos = player.getPositionVector().addVector(0, player.getEyeHeight(), 0);
        Vec3d start = playerPos.subtract(craft.getPositionVector());
        RayTraceResult trace = IBlockEntity.BlockEntityFormer.rayTraceInternal(start.add(craft.getPositionVector()),
                vec.add(craft.getPositionVector()), craft);
        BlockPos pos;
        if (trace == null)
        {
            pos = craft.getPosition();
        }
        else
        {
            pos = trace.getBlockPos();
        }
        IBlockState state = craft.getFakeWorld().getBlockState(pos);
        if (trace != null && state.getBlock() instanceof BlockStairs)
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
                    if (!craft.world.isRemote && !seat.entityId.equals(player.getUniqueID()))
                    {
                        craft.setSeatID(i, player.getUniqueID());
                        player.startRiding(craft);
                    }
                    break;
                }
            }
        }
        else if (craft.rotationYaw != 0)
        {

            for (int i = 0; i < craft.getSeatCount(); i++)
            {
                Seat seat = craft.getSeat(i);
                if (!craft.world.isRemote && seat.entityId.equals(Seat.BLANK))
                {
                    craft.setSeatID(i, player.getUniqueID());
                    player.startRiding(craft);
                    break;
                }
            }
        }
        return EnumActionResult.PASS;
    }

    @Override
    public boolean processInitialInteract(EntityPlayer player, @Nullable ItemStack stack, EnumHand hand)
    {
        if (stack.getItem() == Items.BLAZE_ROD)
        {
            if (!player.world.isRemote)
            {
                craft.setDead();
                return true;
            }
        }
        return false;
    }
}
