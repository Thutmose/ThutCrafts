package thut.crafts.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import thut.crafts.entity.CraftController;
import thut.crafts.entity.EntityCraft;

public class PacketCraftControl implements IMessage, IMessageHandler<PacketCraftControl, IMessage>
{
    private static final short FORWARD = 1;
    private static final short BACK    = 2;
    private static final short LEFT    = 4;
    private static final short RIGHT   = 8;
    private static final short UP      = 16;
    private static final short DOWN    = 32;
    private static final short RLEFT   = 64;
    private static final short RRIGHT  = 128;

    int                        entityId;
    short                      message;

    public static void sendControlPacket(Entity pokemob, CraftController controller)
    {
        PacketCraftControl packet = new PacketCraftControl();
        packet.entityId = pokemob.getEntityId();
        if (controller.backInputDown) packet.message += BACK;
        if (controller.forwardInputDown) packet.message += FORWARD;
        if (controller.leftInputDown) packet.message += LEFT;
        if (controller.rightInputDown) packet.message += RIGHT;
        if (controller.upInputDown) packet.message += UP;
        if (controller.downInputDown) packet.message += DOWN;
        if (controller.leftRotateDown) packet.message += RLEFT;
        if (controller.rightRotateDown) packet.message += RRIGHT;
        PacketPipeline.packetPipeline.sendToServer(packet);
    }

    public PacketCraftControl()
    {
    }

    @Override
    public IMessage onMessage(final PacketCraftControl message, final MessageContext ctx)
    {
        FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(new Runnable()
        {
            @Override
            public void run()
            {
                processMessage(ctx, message);
            }
        });
        return null;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        entityId = buf.readInt();
        message = buf.readShort();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(entityId);
        buf.writeShort(message);
    }

    void processMessage(MessageContext ctx, PacketCraftControl message)
    {
        EntityPlayer player = null;
        if (ctx.side == Side.SERVER)
        {
            player = ctx.getServerHandler().player;
        }
        Entity mob = player.getEntityWorld().getEntityByID(message.entityId);
        if (mob != null && mob instanceof EntityCraft)
        {
            CraftController controller = ((EntityCraft) mob).controller;
            controller.forwardInputDown = (message.message & FORWARD) > 0;
            controller.backInputDown = (message.message & BACK) > 0;
            controller.leftInputDown = (message.message & LEFT) > 0;
            controller.rightInputDown = (message.message & RIGHT) > 0;
            controller.upInputDown = (message.message & UP) > 0;
            controller.downInputDown = (message.message & DOWN) > 0;
            controller.leftRotateDown = (message.message & RLEFT) > 0;
            controller.rightRotateDown = (message.message & RRIGHT) > 0;
        }
    }
}
