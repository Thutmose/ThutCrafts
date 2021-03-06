package thut.crafts;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import thut.api.entity.blockentity.IBlockEntity;
import thut.api.entity.blockentity.RenderBlockEntity;
import thut.api.maths.Vector3;
import thut.crafts.entity.CraftController;
import thut.crafts.entity.EntityCraft;
import thut.crafts.network.PacketCraftControl;
import thut.crafts.network.PacketPipeline;
import thut.crafts.network.PacketPipeline.ClientPacket;
import thut.crafts.network.PacketPipeline.ClientPacket.MessageHandlerClient;
import thut.crafts.network.PacketPipeline.ServerPacket;
import thut.crafts.network.PacketPipeline.ServerPacket.MessageHandlerServer;

@Mod(modid = Reference.MODID, name = "ThutCrafts", dependencies = "required-after:thutcore", version = Reference.VERSION)
public class ThutCrafts
{
    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        MinecraftForge.EVENT_BUS.register(this);
        EntityRegistry.registerModEntity(EntityCraft.class, "craft", 1, this, 32, 1, true);
        DataSerializers.registerSerializer(EntityCraft.SEATSERIALIZER);
        PacketPipeline.packetPipeline = NetworkRegistry.INSTANCE.newSimpleChannel(Reference.MODID);
        PacketPipeline.packetPipeline.registerMessage(MessageHandlerClient.class, ClientPacket.class, 0, Side.CLIENT);
        PacketPipeline.packetPipeline.registerMessage(MessageHandlerServer.class, ServerPacket.class, 1, Side.SERVER);

        PacketPipeline.packetPipeline.registerMessage(PacketCraftControl.class, PacketCraftControl.class, 2,
                Side.CLIENT);
        PacketPipeline.packetPipeline.registerMessage(PacketCraftControl.class, PacketCraftControl.class, 3,
                Side.SERVER);

    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void clientTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase == Phase.START || event.player != Minecraft.getMinecraft().thePlayer) return;
        control:
        if (event.player.isRiding() && Minecraft.getMinecraft().currentScreen == null)
        {
            Entity e = event.player.getRidingEntity();
            if (e instanceof EntityCraft)
            {
                CraftController controller = ((EntityCraft) e).controller;
                if (controller == null) break control;
                controller.backInputDown = ((EntityPlayerSP) event.player).movementInput.backKeyDown;
                controller.forwardInputDown = ((EntityPlayerSP) event.player).movementInput.forwardKeyDown;
                controller.leftInputDown = ((EntityPlayerSP) event.player).movementInput.leftKeyDown;
                controller.rightInputDown = ((EntityPlayerSP) event.player).movementInput.rightKeyDown;

                boolean up = false;
                // if (ClientProxyPokecube.mobUp.getKeyCode() ==
                // Keyboard.KEY_NONE)
                {
                    up = Keyboard.isKeyDown(Keyboard.KEY_SPACE);
                }
                // else
                // {
                // up = GameSettings.isKeyDown(ClientProxyPokecube.mobUp);
                // }
                boolean down = false;
                // if (ClientProxyPokecube.mobDown.getKeyCode() ==
                // Keyboard.KEY_NONE)
                {
                    down = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL);
                }
                // else
                // {
                // down = GameSettings.isKeyDown(ClientProxyPokecube.mobDown);
                // }
                controller.upInputDown = up;
                controller.downInputDown = down;
                PacketCraftControl.sendControlPacket(e, controller);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void RenderBounds(DrawBlockHighlightEvent event)
    {
        ItemStack held;
        EntityPlayer player = event.getPlayer();
        if ((held = player.getHeldItemMainhand()) != null || (held = player.getHeldItemOffhand()) != null)
        {
            BlockPos pos = event.getTarget().getBlockPos();
            if (pos == null) return;
            if (!player.worldObj.getBlockState(pos).getMaterial().isSolid())
            {
                Vec3d loc = player.getPositionVector().addVector(0, player.getEyeHeight(), 0)
                        .add(player.getLookVec().scale(2));
                pos = new BlockPos(loc);
            }

            if (held.getTagCompound() != null && held.getTagCompound().hasKey("min"))
            {
                BlockPos min = Vector3.readFromNBT(held.getTagCompound().getCompoundTag("min"), "").getPos();
                BlockPos max = pos;
                AxisAlignedBB box = new AxisAlignedBB(min, max);
                min = new BlockPos(box.minX, box.minY, box.minZ);
                max = new BlockPos(box.maxX, box.maxY, box.maxZ).add(1, 1, 1);
                box = new AxisAlignedBB(min, max);
                float partialTicks = event.getPartialTicks();
                double d0 = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double) partialTicks;
                double d1 = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double) partialTicks;
                double d2 = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double) partialTicks;
                box = box.offset(-d0, -d1, -d2);
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO);
                GlStateManager.color(0.0F, 0.0F, 0.0F, 0.4F);
                GlStateManager.glLineWidth(2.0F);
                GlStateManager.disableTexture2D();
                GlStateManager.depthMask(false);
                GlStateManager.color(1.0F, 0.0F, 0.0F, 1F);
                Tessellator tessellator = Tessellator.getInstance();
                VertexBuffer vertexbuffer = tessellator.getBuffer();
                vertexbuffer.begin(3, DefaultVertexFormats.POSITION);
                vertexbuffer.pos(box.minX, box.minY, box.minZ).endVertex();
                vertexbuffer.pos(box.maxX, box.minY, box.minZ).endVertex();
                vertexbuffer.pos(box.maxX, box.minY, box.maxZ).endVertex();
                vertexbuffer.pos(box.minX, box.minY, box.maxZ).endVertex();
                vertexbuffer.pos(box.minX, box.minY, box.minZ).endVertex();
                tessellator.draw();
                vertexbuffer.begin(3, DefaultVertexFormats.POSITION);
                vertexbuffer.pos(box.minX, box.maxY, box.minZ).endVertex();
                vertexbuffer.pos(box.maxX, box.maxY, box.minZ).endVertex();
                vertexbuffer.pos(box.maxX, box.maxY, box.maxZ).endVertex();
                vertexbuffer.pos(box.minX, box.maxY, box.maxZ).endVertex();
                vertexbuffer.pos(box.minX, box.maxY, box.minZ).endVertex();
                tessellator.draw();
                vertexbuffer.begin(1, DefaultVertexFormats.POSITION);
                vertexbuffer.pos(box.minX, box.minY, box.minZ).endVertex();
                vertexbuffer.pos(box.minX, box.maxY, box.minZ).endVertex();
                vertexbuffer.pos(box.maxX, box.minY, box.minZ).endVertex();
                vertexbuffer.pos(box.maxX, box.maxY, box.minZ).endVertex();
                vertexbuffer.pos(box.maxX, box.minY, box.maxZ).endVertex();
                vertexbuffer.pos(box.maxX, box.maxY, box.maxZ).endVertex();
                vertexbuffer.pos(box.minX, box.minY, box.maxZ).endVertex();
                vertexbuffer.pos(box.minX, box.maxY, box.maxZ).endVertex();
                tessellator.draw();
                GlStateManager.depthMask(true);
                GlStateManager.enableTexture2D();
                GlStateManager.disableBlend();
            }
        }
    }

    @SideOnly(Side.CLIENT)
    @EventHandler
    public void clientPreInit(FMLPreInitializationEvent event)
    {
        RenderingRegistry.registerEntityRenderingHandler(EntityCraft.class, new IRenderFactory<EntityLivingBase>()
        {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            @Override
            public Render<? super EntityLivingBase> createRenderFor(RenderManager manager)
            {
                return new RenderBlockEntity(manager);
            }
        });
    }

    @SubscribeEvent
    public void interactRightClickBlock(PlayerInteractEvent.RightClickItem evt)
    {
        if (evt.getHand() == EnumHand.OFF_HAND || evt.getWorld().isRemote || evt.getItemStack() == null
                || !evt.getEntityPlayer().isSneaking() || evt.getItemStack().getItem() != Items.STICK)
            return;
        ItemStack itemstack = evt.getItemStack();
        EntityPlayer playerIn = evt.getEntityPlayer();
        World worldIn = evt.getWorld();
        if (!itemstack.getDisplayName().equals("Craft")) return;
        if (itemstack.hasTagCompound() && playerIn.isSneaking() && itemstack.getTagCompound().hasKey("min")
                && itemstack.getTagCompound().getLong("time") != worldIn.getWorldTime())
        {
            NBTTagCompound minTag = itemstack.getTagCompound().getCompoundTag("min");
            Vec3d loc = playerIn.getPositionVector().addVector(0, playerIn.getEyeHeight(), 0)
                    .add(playerIn.getLookVec().scale(2));
            BlockPos pos = new BlockPos(loc);
            BlockPos min = pos;
            BlockPos max = Vector3.readFromNBT(minTag, "").getPos();
            AxisAlignedBB box = new AxisAlignedBB(min, max);
            min = new BlockPos(box.minX, box.minY, box.minZ);
            max = new BlockPos(box.maxX, box.maxY, box.maxZ);
            BlockPos mid = min.add((max.getX() - min.getX()) / 2, 0, (max.getZ() - min.getZ()) / 2);
            min = min.subtract(mid);
            max = max.subtract(mid);
            int dw = Math.max(max.getX() - min.getX(), max.getZ() - min.getZ());
            if (max.getY() - min.getY() > 15 || dw > 2 * 10 + 1)
            {
                String message = "msg.lift.toobig";
                if (!worldIn.isRemote) playerIn.addChatMessage(new TextComponentTranslation(message));
                return;
            }
            if (!worldIn.isRemote)
            {
                IBlockEntity.BlockEntityFormer.makeBlockEntity(evt.getWorld(), min, max, mid, EntityCraft.class);
                String message = "msg.lift.create";
                playerIn.addChatMessage(new TextComponentTranslation(message));
            }
            itemstack.getTagCompound().removeTag("min");
        }
    }

    /** Uses player interact here to also prevent opening of inventories.
     * 
     * @param evt */
    @SubscribeEvent
    public void interactRightClickBlock(PlayerInteractEvent.RightClickBlock evt)
    {
        if (evt.getHand() == EnumHand.OFF_HAND || evt.getWorld().isRemote || evt.getItemStack() == null
                || !evt.getEntityPlayer().isSneaking() || evt.getItemStack().getItem() != Items.STICK)
            return;
        ItemStack itemstack = evt.getItemStack();
        if (!itemstack.getDisplayName().equals("Craft")) return;
        EntityPlayer playerIn = evt.getEntityPlayer();
        World worldIn = evt.getWorld();
        BlockPos pos = evt.getPos();
        if (itemstack.hasTagCompound() && playerIn.isSneaking() && itemstack.getTagCompound().hasKey("min"))
        {
            NBTTagCompound minTag = itemstack.getTagCompound().getCompoundTag("min");
            BlockPos min = pos;
            BlockPos max = Vector3.readFromNBT(minTag, "").getPos();
            AxisAlignedBB box = new AxisAlignedBB(min, max);
            min = new BlockPos(box.minX, box.minY, box.minZ);
            max = new BlockPos(box.maxX, box.maxY, box.maxZ);
            BlockPos mid = min.add((max.getX() - min.getX()) / 2, 0, (max.getZ() - min.getZ()) / 2);
            min = min.subtract(mid);
            max = max.subtract(mid);
            int dw = Math.max(max.getX() - min.getX(), max.getZ() - min.getZ());
            if (max.getY() - min.getY() > 10 || dw > 2 * 5 + 1)
            {
                String message = "msg.lift.toobig";
                if (!worldIn.isRemote) playerIn.addChatMessage(new TextComponentTranslation(message));
                return;
            }
            if (!worldIn.isRemote)
            {
                IBlockEntity.BlockEntityFormer.makeBlockEntity(evt.getWorld(), min, max, mid, EntityCraft.class);
                String message = "msg.lift.create";
                playerIn.addChatMessage(new TextComponentTranslation(message));
            }
            itemstack.getTagCompound().removeTag("min");
            evt.setCanceled(true);
        }
        else
        {
            if (!itemstack.hasTagCompound()) itemstack.setTagCompound(new NBTTagCompound());
            NBTTagCompound min = new NBTTagCompound();
            Vector3.getNewVector().set(pos).writeToNBT(min, "");
            itemstack.getTagCompound().setTag("min", min);
            String message = "msg.lift.setcorner";
            if (!worldIn.isRemote) playerIn.addChatMessage(new TextComponentTranslation(message, pos));
            evt.setCanceled(true);
            itemstack.getTagCompound().setLong("time", worldIn.getWorldTime());
        }
    }

    @SubscribeEvent
    public void logout(PlayerLoggedOutEvent event)
    {
        if (event.player.isRiding() && event.player.getLowestRidingEntity() instanceof EntityCraft)
        {
            event.player.dismountRidingEntity();
        }
    }
}
