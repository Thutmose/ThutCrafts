/**
 *
 */
package thut.crafts.network;

import javax.vecmath.Vector3f;
import javax.xml.ws.handler.MessageContext;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.network.PacketDistributor.TargetPoint;
import thut.api.maths.Vector3;
import thut.core.common.ThutCore;

public class PacketPipeline
{

    public static class ClientPacket implements IMessage
    {
        public static class MessageHandlerClient implements IMessageHandler<ClientPacket, ServerPacket>
        {
            @Override
            public ServerPacket onMessage(ClientPacket message, MessageContext ctx)
            {
                ByteBuf buffer = message.buffer;
                byte mess = buffer.readByte();
                if (mess == 0)
                {
                    double y = buffer.readDouble();
                    double dy = buffer.readDouble();
                    PlayerEntity player = ThutCore.proxy.getPlayer();
                    y += player.getYOffset();
                    player.motionY = Math.max(dy, player.motionY);
                    ThutCore.proxy.getPlayer().setPosition(player.posX, y, player.posZ);
                }
                return null;
            }
        }

        PacketBuffer buffer;;

        public ClientPacket()
        {
        }

        public ClientPacket(byte channel, CompoundNBT nbt)
        {
            this.buffer = new PacketBuffer(Unpooled.buffer());
            buffer.writeByte(channel);
            buffer.writeCompoundTag(nbt);
        }

        public ClientPacket(byte[] data)
        {
            this.buffer = new PacketBuffer(Unpooled.buffer());
            buffer.writeBytes(data);
        }

        public ClientPacket(ByteBuf buffer)
        {
            this.buffer = new PacketBuffer(buffer);
        }

        @Override
        public void fromBytes(ByteBuf buf)
        {
            if (buffer == null)
            {
                buffer = new PacketBuffer(Unpooled.buffer());
            }
            buffer.writeBytes(buf);
        }

        @Override
        public void toBytes(ByteBuf buf)
        {
            if (buffer == null)
            {
                buffer = new PacketBuffer(Unpooled.buffer());
            }
            buf.writeBytes(buffer);
        }
    }

    public static class ServerPacket implements IMessage
    {
        public static class MessageHandlerServer implements IMessageHandler<ServerPacket, IMessage>
        {

            public void handleServerSide(PlayerEntity player, PacketBuffer buffer)
            {

                final Vector3f hit = new Vector3f(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
                final Direction side = Direction.values()[buffer.readByte()];
                final BlockPos pos = buffer.readBlockPos();
                final PlayerEntity player1 = player;
                FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        IBlockState state = player1.world.getBlockState(pos);
                        state.getBlock().onBlockActivated(player1.world, pos, state, player1, Hand.MAIN_HAND,
                                side, hit.x, hit.y, hit.z);
                    }
                });
            }

            @Override
            public ServerPacket onMessage(ServerPacket message, MessageContext ctx)
            {
                PlayerEntity player = ctx.getServerHandler().player;
                handleServerSide(player, message.buffer);

                return null;
            }
        }

        PacketBuffer buffer;;

        public ServerPacket()
        {
        }

        public ServerPacket(byte channel, CompoundNBT nbt)
        {
            this.buffer = new PacketBuffer(Unpooled.buffer());
            buffer.writeByte(channel);
            buffer.writeCompoundTag(nbt);
        }

        public ServerPacket(byte[] data)
        {
            this.buffer = new PacketBuffer(Unpooled.buffer());
            buffer.writeBytes(data);
        }

        public ServerPacket(ByteBuf buffer)
        {
            this.buffer = (PacketBuffer) buffer;
        }

        @Override
        public void fromBytes(ByteBuf buf)
        {
            if (buffer == null)
            {
                buffer = new PacketBuffer(Unpooled.buffer());
            }
            buffer.writeBytes(buf);
        }

        @Override
        public void toBytes(ByteBuf buf)
        {
            if (buffer == null)
            {
                buffer = new PacketBuffer(Unpooled.buffer());
            }
            buf.writeBytes(buffer);
        }
    }

    public static SimpleNetworkWrapper packetPipeline;

    public static int ByteArrayAsInt(byte[] stats)
    {
        if (stats.length != 4) return 0;
        int value = 0;
        for (int i = 3; i >= 0; i--)
        {
            value = (value << 8) + (stats[i] & 0xff);
        }
        return value;
    }

    public static byte[] intAsByteArray(int ints)
    {
        byte[] stats = new byte[] { (byte) ((ints & 0xFF)), (byte) ((ints >> 8 & 0xFF)), (byte) ((ints >> 16 & 0xFF)),
                (byte) ((ints >> 24 & 0xFF)), };
        return stats;
    }

    public static ClientPacket makeClientPacket(byte channel, byte[] data)
    {
        byte[] packetData = new byte[data.length + 1];
        packetData[0] = channel;

        for (int i = 1; i < packetData.length; i++)
        {
            packetData[i] = data[i - 1];
        }
        return new ClientPacket(packetData);
    }

    public static ClientPacket makeClientPacket(byte channel, CompoundNBT nbt)
    {
        PacketBuffer packetData = new PacketBuffer(Unpooled.buffer());
        packetData.writeByte(channel);
        packetData.writeCompoundTag(nbt);

        return new ClientPacket(packetData);
    }

    public static ServerPacket makePacket(byte channel, CompoundNBT nbt)
    {
        PacketBuffer packetData = new PacketBuffer(Unpooled.buffer());
        packetData.writeByte(channel);
        packetData.writeCompoundTag(nbt);

        return new ServerPacket(packetData);
    }

    public static ServerPacket makeServerPacket(byte channel, byte[] data)
    {
        byte[] packetData = new byte[data.length + 1];
        packetData[0] = channel;

        for (int i = 1; i < packetData.length; i++)
        {
            packetData[i] = data[i - 1];
        }
        return new ServerPacket(packetData);
    }

    public static void sendToAll(IMessage toSend)
    {
        packetPipeline.sendToAll(toSend);
    }

    public static void sendToAllNear(IMessage toSend, Vector3 point, int dimID, double distance)
    {
        packetPipeline.sendToAllAround(toSend, new TargetPoint(dimID, point.x, point.y, point.z, distance));
    }

    public static void sendToClient(IMessage toSend, PlayerEntity player)
    {
        if (player == null)
        {
            System.out.println("null player");
            return;
        }
        packetPipeline.sendTo(toSend, (ServerPlayerEntity) player);
    }

    public static void sendToServer(IMessage toSend)
    {
        packetPipeline.sendToServer(toSend);
    }

}
