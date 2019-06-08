package thut.crafts;

import com.mojang.blaze3d.platform.GlStateManager;

import net.java.games.input.Keyboard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
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

@Mod(Reference.MODID)
@EventBusSubscriber
public class ThutCrafts
{
    private boolean canRotate = false;

    @EventHandler
    public void preInit(FMLCommonSetupEvent event)
    {
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        canRotate = config.getBoolean("canRotate", Configuration.CATEGORY_GENERAL, canRotate,
                "Can the crafts rotate? WARNING, YOU CANNOT STAND ON A ROTATED CRAFT!");
        config.save();

        MinecraftForge.EVENT_BUS.register(this);
        EntityRegistry.registerModEntity(new ResourceLocation("thutcrafts:craft"), EntityCraft.class, "craft", 1, this,
                32, 1, true);
        PacketPipeline.packetPipeline = NetworkRegistry.INSTANCE.newSimpleChannel(Reference.MODID);
        PacketPipeline.packetPipeline.registerMessage(MessageHandlerClient.class, ClientPacket.class, 0, Dist.CLIENT);
        PacketPipeline.packetPipeline.registerMessage(MessageHandlerServer.class, ServerPacket.class, 1, Dist.DEDICATED_SERVER);

        PacketPipeline.packetPipeline.registerMessage(PacketCraftControl.class, PacketCraftControl.class, 2,
                Dist.CLIENT);
        PacketPipeline.packetPipeline.registerMessage(PacketCraftControl.class, PacketCraftControl.class, 3,
                Dist.DEDICATED_SERVER);

    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void clientTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase == Phase.START || event.player != Minecraft.getInstance().player) return;
        control:
        if (event.player.isPassenger() && Minecraft.getInstance().currentScreen == null)
        {
            Entity e = event.player.getRidingEntity();
            if (e instanceof EntityCraft)
            {
                ClientPlayerEntity player = ((ClientPlayerEntity) event.player);
                CraftController controller = ((EntityCraft) e).controller;
                if (controller == null) break control;
                controller.backInputDown = player.movementInput.backKeyDown;
                controller.forwardInputDown = player.movementInput.forwardKeyDown;
                controller.leftInputDown = player.movementInput.leftKeyDown;
                controller.rightInputDown = player.movementInput.rightKeyDown;
                controller.upInputDown = Keyboard.isKeyDown(Keyboard.KEY_SPACE);
                controller.downInputDown = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL);
                if (canRotate)
                {
                    controller.rightRotateDown = Keyboard.isKeyDown(Keyboard.KEY_RBRACKET);
                    controller.leftRotateDown = Keyboard.isKeyDown(Keyboard.KEY_LBRACKET);
                }
                PacketCraftControl.sendControlPacket(e, controller);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void RenderBounds(DrawBlockHighlightEvent event)
    {
        ItemStack held;
        PlayerEntity player = event.getPlayer();
        if ((held = player.getHeldItemMainhand()) != null || (held = player.getHeldItemOffhand()) != null)
        {
            BlockPos pos = event.getTarget().getBlockPos();
            if (pos == null) return;
            if (!player.world.getBlockState(pos).getMaterial().isSolid())
            {
                Vec3d loc = player.getPositionVector().add(0, player.getEyeHeight(), 0)
                        .add(player.getLookVec().scale(2));
                pos = new BlockPos(loc);
            }

            if (held.getTag() != null && held.getTag().hasKey("min"))
            {
                BlockPos min = Vector3.readFromNBT(held.getTag().getCompound("min"), "").getPos();
                BlockPos max = pos;
                AxisAlignedBB box = new AxisAlignedBB(min, max);
                min = new BlockPos(box.minX, box.minY, box.minZ);
                max = new BlockPos(box.maxX, box.maxY, box.maxZ).add(1, 1, 1);
                box = new AxisAlignedBB(min, max);
                float partialTicks = event.getPartialTicks();
                double d0 = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
                double d1 = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
                double d2 = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
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
                BufferBuilder vertexbuffer = tessellator.getBuffer();
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

    @OnlyIn(Dist.CLIENT)
    @EventHandler
    public void clientPreInit(FMLCommonSetupEvent event)
    {
        RenderingRegistry.registerEntityRenderingHandler(EntityCraft.class, new IRenderFactory<LivingEntity>()
        {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            @Override
            public Render<? super LivingEntity> createRenderFor(RenderManager manager)
            {
                return new RenderBlockEntity(manager);
            }
        });
    }

    @SubscribeEvent
    public void interactRightClickBlock(PlayerInteractEvent.RightClickItem evt)
    {
        if (evt.getHand() == Hand.OFF_HAND || evt.getWorld().isRemote || evt.getItemStack() == null
                || !evt.getPlayerEntity().isSneaking() || evt.getItemStack().getItem() != Items.STICK)
            return;
        ItemStack itemstack = evt.getItemStack();
        PlayerEntity playerIn = evt.getPlayerEntity();
        World worldIn = evt.getWorld();
        if (!itemstack.getDisplayName().equals("Craft")) return;
        if (itemstack.hasTag() && playerIn.isSneaking() && itemstack.getTag().contains("min")
                && itemstack.getTag().getLong("time") != worldIn.getDayTime())
        {
            CompoundNBT minTag = itemstack.getTag().getCompound("min");
            Vec3d loc = playerIn.getPositionVector().add(0, playerIn.getEyeHeight(), 0)
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
                if (!worldIn.isRemote) playerIn.sendMessage(new TranslationTextComponent(message));
                return;
            }
            if (!worldIn.isRemote)
            {
                EntityCraft craft = IBlockEntity.BlockEntityFormer.makeBlockEntity(evt.getWorld(), min, max, mid,
                        EntityCraft.class);
                String message = craft != null ? "msg.craft.create" : "msg.craft.fail";
                playerIn.sendMessage(new TranslationTextComponent(message));
            }
            itemstack.getTag().remove("min");
        }
    }

    /** Uses player interact here to also prevent opening of inventories.
     * 
     * @param evt */
    @SubscribeEvent
    public void interactRightClickBlock(PlayerInteractEvent.RightClickBlock evt)
    {
        if (evt.getHand() == Hand.OFF_HAND || evt.getWorld().isRemote || evt.getItemStack() == null
                || !evt.getEntityPlayer().isSneaking() || evt.getItemStack().getItem() != Items.STICK)
            return;
        ItemStack itemstack = evt.getItemStack();
        if (!itemstack.getDisplayName().equals("Craft")) return;
        PlayerEntity playerIn = evt.getEntityPlayer();
        World worldIn = evt.getWorld();
        BlockPos pos = evt.getPos();
        if (itemstack.hasTag() && playerIn.isSneaking() && itemstack.getTag().contains("min"))
        {
            CompoundNBT minTag = itemstack.getTag().getCompound("min");
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
                String message = "msg.craft.toobig";
                if (!worldIn.isRemote) playerIn.sendMessage(new TranslationTextComponent(message));
                return;
            }
            if (!worldIn.isRemote)
            {
                EntityCraft craft = IBlockEntity.BlockEntityFormer.makeBlockEntity(evt.getWorld(), min, max, mid,
                        EntityCraft.class);
                String message = craft != null ? "msg.craft.create" : "msg.craft.fail";
                playerIn.sendMessage(new TranslationTextComponent(message));
            }
            itemstack.getTag().remove("min");
            evt.setCanceled(true);
        }
        else
        {
            if (!itemstack.hasTag()) itemstack.setTag(new CompoundNBT());
            CompoundNBT min = new CompoundNBT();
            Vector3.getNewVector().set(pos).writeToNBT(min, "");
            itemstack.getTag().put("min", min);
            String message = "msg.lift.setcorner";
            if (!worldIn.isRemote) playerIn.sendMessage(new TranslationTextComponent(message, pos));
            evt.setCanceled(true);
            itemstack.getTag().putLong("time", worldIn.getDayTime());
        }
    }

    @SubscribeEvent
    public void logout(PlayerLoggedOutEvent event)
    {
        if (event.getPlayer().isPassenger() && event.getPlayer().getLowestRidingEntity() instanceof EntityCraft)
        {
            event.getPlayer().stopRiding();
        }
    }
}
