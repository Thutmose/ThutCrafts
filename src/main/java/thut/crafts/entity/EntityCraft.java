package thut.crafts.entity;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.vecmath.Vector3f;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializer;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import thut.api.entity.IMultiplePassengerEntity;
import thut.api.entity.blockentity.BlockEntityUpdater;
import thut.api.entity.blockentity.BlockEntityWorld;
import thut.api.entity.blockentity.IBlockEntity;
import thut.api.maths.Vector3;

public class EntityCraft extends EntityLivingBase
        implements IEntityAdditionalSpawnData, IBlockEntity, IMultiplePassengerEntity
{
    public static class Seat
    {
        private static final UUID BLANK = new UUID(0, 0);
        Vector3f                  seat;
        UUID                      entityId;

        public Seat(Vector3f vector3f, UUID readInt)
        {
            seat = vector3f;
            entityId = readInt != null ? readInt : BLANK;
        }

        public Seat(PacketBuffer buf)
        {
            seat = new Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat());
            entityId = new UUID(buf.readLong(), buf.readLong());
        }

        public void writeToBuf(PacketBuffer buf)
        {
            buf.writeFloat(seat.x);
            buf.writeFloat(seat.y);
            buf.writeFloat(seat.z);
            buf.writeLong(entityId.getMostSignificantBits());
            buf.writeLong(entityId.getLeastSignificantBits());
        }
    }

    public static final DataSerializer<Seat> SEATSERIALIZER     = new DataSerializer<Seat>()
                                                                {
                                                                    @Override
                                                                    public void write(PacketBuffer buf, Seat value)
                                                                    {
                                                                        value.writeToBuf(buf);
                                                                    }

                                                                    @Override
                                                                    public Seat read(PacketBuffer buf)
                                                                            throws IOException
                                                                    {
                                                                        return new Seat(buf);
                                                                    }

                                                                    @Override
                                                                    public DataParameter<Seat> createKey(int id)
                                                                    {
                                                                        return new DataParameter<>(id, this);
                                                                    }
                                                                };

    @SuppressWarnings("unchecked")
    static final DataParameter<Seat>[]       SEAT               = new DataParameter[10];
    static final DataParameter<Integer>      SEATCOUNT          = EntityDataManager
            .<Integer> createKey(EntityCraft.class, DataSerializers.VARINT);
    static final DataParameter<Integer>      DESTINATIONFLOORDW = EntityDataManager
            .<Integer> createKey(EntityCraft.class, DataSerializers.VARINT);
    static final DataParameter<Float>        DESTINATIONYDW     = EntityDataManager.<Float> createKey(EntityCraft.class,
            DataSerializers.FLOAT);
    static final DataParameter<Float>        DESTINATIONXDW     = EntityDataManager.<Float> createKey(EntityCraft.class,
            DataSerializers.FLOAT);
    static final DataParameter<Float>        DESTINATIONZDW     = EntityDataManager.<Float> createKey(EntityCraft.class,
            DataSerializers.FLOAT);
    static final DataParameter<Integer>      MAINSEATDW         = EntityDataManager
            .<Integer> createKey(EntityCraft.class, DataSerializers.VARINT);
    static final DataParameter<Boolean>      CALLEDDW           = EntityDataManager
            .<Boolean> createKey(EntityCraft.class, DataSerializers.BOOLEAN);

    static
    {
        for (int i = 0; i < SEAT.length; i++)
        {
            SEAT[i] = EntityDataManager.<Seat> createKey(EntityCraft.class, SEATSERIALIZER);
        }
    }

    public static int          ACCELERATIONTICKS = 20;

    public static boolean      ENERGYUSE         = false;
    public static int          ENERGYCOST        = 100;

    public BlockPos            boundMin          = BlockPos.ORIGIN;
    public BlockPos            boundMax          = BlockPos.ORIGIN;

    public CraftController     controller        = new CraftController(this);
    int                        energy            = 0;
    private BlockEntityWorld   world;
    public double              speedUp           = 0.5;
    public double              speedDown         = -0.5;
    public double              speedHoriz        = 0.5;
    public double              acceleration      = 0.05;
    public boolean             toMoveY           = false;
    public boolean             toMoveX           = false;
    public boolean             toMoveZ           = false;
    public boolean             axis              = true;
    public boolean             hasPassenger      = false;
    int                        n                 = 0;
    int                        passengertime     = 10;
    boolean                    first             = true;
    Random                     r                 = new Random();
    public UUID                id                = null;
    public UUID                owner;
    public List<AxisAlignedBB> blockBoxes        = Lists.newArrayList();
    public ItemStack[][][]     blocks            = null;
    public TileEntity[][][]    tiles             = null;
    BlockEntityUpdater         collider;
    CraftInteractHandler       interacter;

    public EntityCraft(World par1World)
    {
        super(par1World);
        this.ignoreFrustumCheck = true;
        this.hurtResistantTime = 0;
        this.isImmuneToFire = true;
    }

    public BlockEntityWorld getFakeWorld()
    {
        if (world == null)
        {
            world = new BlockEntityWorld(this, worldObj);
        }
        return world;
    }

    public EntityCraft(World world, double x, double y, double z)
    {
        this(world);
        this.setPosition(x, y, z);
        r.setSeed(100);
    }

    public void addSeat(Vector3f seat)
    {
        Seat toSet = this.getSeat(getSeatCount());
        toSet.seat.set(seat);
        this.dataManager.set(SEAT[getSeatCount()], toSet);
        this.dataManager.setDirty(SEAT[getSeatCount()]);
        setSeatCount(getSeatCount() + 1);
    }

    void setSeatID(int index, UUID id)
    {
        Seat toSet = this.getSeat(index);
        UUID old = toSet.entityId;
        if (!old.equals(id))
        {
            toSet.entityId = id;
            this.dataManager.set(SEAT[index], toSet);
            this.dataManager.setDirty(SEAT[index]);
        }
        System.out.println(id + " " + old);
    }

    Seat getSeat(int index)
    {
        return this.dataManager.get(SEAT[index]);
    }

    @Override
    /** Called when the entity is attacked. */
    public boolean attackEntityFrom(DamageSource source, float amount)
    {
        return false;
    }

    @Override
    /** knocks back this entity */
    public void knockBack(Entity entityIn, float strenght, double xRatio, double zRatio)
    {

    }

    private double getSpeed(double pos, double destPos, double speed, double speedPos, double speedNeg)
    {
        double ds = speed;
        if (destPos > pos)
        {
            boolean tooFast = pos + (ds * (ACCELERATIONTICKS + 1)) > destPos;
            if (!tooFast)
            {
                ds = Math.min(speedPos, ds + acceleration * speedPos);
            }
            else while (ds >= 0 && tooFast)
            {
                ds = ds - acceleration * speedPos / 10;
                tooFast = pos + (ds * (ACCELERATIONTICKS + 1)) > destPos;
            }
        }
        else
        {
            speedNeg = Math.abs(speedNeg);
            boolean tooFast = pos + (ds * (ACCELERATIONTICKS + 1)) < destPos;
            if (!tooFast)
            {
                ds = Math.max(-speedNeg, ds - acceleration * speedNeg);
            }
            else while (ds <= 0 && tooFast)
            {
                ds = ds + acceleration * speedNeg / 10;
                tooFast = pos + (ds * (ACCELERATIONTICKS + 1)) < destPos;
            }
        }
        return ds;
    }

    private void accelerate()
    {
        if (isServerWorld() && !consumePower())
        {
            toMoveY = toMoveX = toMoveZ = false;
        }
        if (!toMoveX) motionX *= 0.5;
        if (!toMoveZ) motionZ *= 0.5;
        if (!toMoveY) motionY *= 0.5;
        if (toMoveY)
        {
            float destY = (float) (controller.upInputDown ? posY + 30 : posY - 30);
            double dy = getSpeed(posY, destY, motionY, speedUp, speedDown);
            motionY = dy;
        }
        if (toMoveX)
        {
            float destX = (float) (controller.leftInputDown ? posX + 30 : posX - 30);
            double dx = getSpeed(posX, destX, motionX, speedHoriz, speedHoriz);
            motionX = dx;
        }
        if (toMoveZ)
        {
            float destZ = (float) (controller.forwardInputDown ? posZ + 30 : posZ - 30);
            double dz = getSpeed(posZ, destZ, motionZ, speedHoriz, speedHoriz);
            motionZ = dz;
        }
    }

    @Override
    public void updatePassenger(Entity passenger)
    {
        if (this.isPassenger(passenger))
        {
            if (passenger.isSneaking())
            {
                passenger.dismountRidingEntity();
            }
            IMultiplePassengerEntity.MultiplePassengerManager.managePassenger(passenger, this);
        }
    }

    @Override
    protected void addPassenger(Entity passenger)
    {
        super.addPassenger(passenger);
    }

    @Override
    protected void removePassenger(Entity passenger)
    {
        super.removePassenger(passenger);
        if (!worldObj.isRemote) for (int i = 0; i < getSeatCount(); i++)
        {
            if (getSeat(i).entityId.equals(passenger.getUniqueID()))
            {
                setSeatID(i, Seat.BLANK);
                break;
            }
        }
    }

    @Override
    protected boolean canFitPassenger(Entity passenger)
    {
        return this.getPassengers().size() < getSeatCount();
    }

    /** If a rider of this entity can interact with this entity. Should return
     * true on the ridden entity if so.
     *
     * @return if the entity can be interacted with from a rider */
    @Override
    public boolean canRiderInteract()
    {
        return true;
    }

    @Override
    public Vector3f getSeat(Entity passenger)
    {
        Vector3f ret = null;
        for (int i = 0; i < getSeatCount(); i++)
        {
            Seat seat;
            if ((seat = getSeat(i)).entityId.equals(passenger.getUniqueID())) { return seat.seat; }
        }
        return ret;
    }

    @Override
    public Entity getPassenger(Vector3f seatl)
    {
        UUID id = null;
        for (int i = 0; i < getSeatCount(); i++)
        {
            Seat seat;
            if ((seat = getSeat(i)).seat.equals(seatl))
            {
                id = seat.entityId;
            }
        }
        if (id != null)
        {
            for (Entity e : getPassengers())
            {
                if (e.getUniqueID().equals(id)) return e;
            }
        }
        return null;
    }

    @Override
    public List<Vector3f> getSeats()
    {
        List<Vector3f> ret = Lists.newArrayList();
        for (int i = 0; i < getSeatCount(); i++)
        {
            Seat seat = getSeat(i);
            ret.add(seat.seat);
        }
        return null;
    }

    @Override
    public float getYaw()
    {
        return this.rotationYaw;
    }

    @Override
    public float getPitch()
    {
        // TODO datawatcher value of pitch.
        return this.rotationPitch;
    }

    @Override
    public float getPrevYaw()
    {
        return prevRotationYaw;
    }

    @Override
    public float getPrevPitch()
    {
        return prevRotationPitch;
    }

    int getSeatCount()
    {
        return dataManager.get(SEATCOUNT);
    }

    void setSeatCount(int count)
    {
        dataManager.set(SEATCOUNT, count);
    }

    /** Applies a velocity to each of the entities pushing them away from each
     * other. Args: entity */
    @Override
    public void applyEntityCollision(Entity entity)
    {
        if (collider == null)
        {
            collider = new BlockEntityUpdater(this);
            collider.onSetPosition();
        }
        try
        {
            collider.applyEntityCollision(entity);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /** Returns true if other Entities should be prevented from moving through
     * this Entity. */
    @Override
    public boolean canBeCollidedWith()
    {
        return !this.isDead;
    }

    /** Returns true if this entity should push and be pushed by other entities
     * when colliding. */
    @Override
    public boolean canBePushed()
    {
        return true;
    }

    @Override
    public boolean canRenderOnFire()
    {
        return false;
    }

    public void checkCollision()
    {
        int xMin = boundMin.getX();
        int zMin = boundMin.getZ();
        int xMax = boundMax.getX();
        int zMax = boundMax.getZ();

        List<?> list = this.worldObj.getEntitiesWithinAABBExcludingEntity(this, new AxisAlignedBB(posX + (xMin - 1),
                posY, posZ + (zMin - 1), posX + xMax + 1, posY + 64, posZ + zMax + 1));
        if (list != null && !list.isEmpty())
        {
            if (list.size() == 1 && this.getRecursivePassengers() != null
                    && !this.getRecursivePassengers().isEmpty()) { return; }
            for (int i = 0; i < list.size(); ++i)
            {
                Entity entity = (Entity) list.get(i);
                applyEntityCollision(entity);
            }
        }
    }

    private boolean consumePower()
    {
        if (!ENERGYUSE || !getCalled()) return true;
        boolean power = false;
        Vector3 bounds = Vector3.getNewVector().set(boundMax.subtract(boundMin));
        double volume = bounds.x * bounds.y * bounds.z;
        double energyCost = Math.abs(getDestY() - posY) * ENERGYCOST * volume * 0.01;
        energyCost = Math.max(energyCost, 1);
        power = (energy = (int) (energy - energyCost)) > 0;
        if (energy < 0) energy = 0;
        MinecraftForge.EVENT_BUS.post(new EventCraftConsumePower(this, (long) energyCost));
        if (!power)
        {
            this.setDestY((float) posY);
            toMoveY = false;
        }
        return power;
    }

    public int getEnergy()
    {
        return energy;
    }

    public void setEnergy(int energy)
    {
        this.energy = energy;
    }

    public void doMotion()
    {
        this.moveEntity(motionX, motionY, motionZ);
    }

    @Override
    public void resetPositionToBB()
    {
        BlockPos min = getMin();
        BlockPos max = getMax();
        float xDiff = (max.getX() - min.getX()) / 2f;
        float zDiff = (max.getZ() - min.getZ()) / 2f;
        AxisAlignedBB axisalignedbb = this.getEntityBoundingBox();
        if ((xDiff % 1) != 0) this.posX = (axisalignedbb.minX + xDiff);
        else this.posX = (axisalignedbb.minX + axisalignedbb.maxX) / 2.0D;
        this.posY = axisalignedbb.minY;
        if (zDiff % 1 != 0) this.posZ = (axisalignedbb.minZ + zDiff);
        else this.posZ = (axisalignedbb.minZ + axisalignedbb.maxZ) / 2.0D;
    }

    @Override
    protected void entityInit()
    {
        super.entityInit();
        this.dataManager.register(DESTINATIONFLOORDW, Integer.valueOf(0));
        this.dataManager.register(DESTINATIONYDW, Float.valueOf(0));
        this.dataManager.register(DESTINATIONXDW, Float.valueOf(0));
        this.dataManager.register(DESTINATIONZDW, Float.valueOf(0));
        this.dataManager.register(MAINSEATDW, Integer.valueOf(-1));
        this.dataManager.register(CALLEDDW, Boolean.FALSE);
        for (int i = 0; i < 10; i++)
            dataManager.register(SEAT[i], new Seat(new Vector3f(), null));
        dataManager.register(SEATCOUNT, 0);
    }

    /** returns the bounding box for this entity */
    public AxisAlignedBB getBoundingBox()
    {
        return null;
    }

    /** Checks if the entity's current position is a valid location to spawn
     * this entity. */
    public boolean getCanSpawnHere()
    {
        return false;
    }

    @Deprecated
    private boolean getCalled()
    {
        return dataManager.get(CALLEDDW);
    }

    @Deprecated
    private void setCalled(boolean called)
    {
        dataManager.set(CALLEDDW, called);
    }

    /** @return the destinationFloor */
    public int getMainSeat()
    {
        return dataManager.get(MAINSEATDW);
    }

    /** @return the destinationFloor */
    public void setMainSeat(int seat)
    {
        dataManager.set(MAINSEATDW, seat);
    }

    /** @return the destinationFloor */
    public int getDestinationFloor()
    {
        return dataManager.get(DESTINATIONFLOORDW);
    }

    /** @return the destinationFloor */
    public float getDestX()
    {
        return dataManager.get(DESTINATIONXDW);
    }

    /** @return the destinationFloor */
    public float getDestY()
    {
        return dataManager.get(DESTINATIONYDW);
    }

    /** @return the destinationFloor */
    public float getDestZ()
    {
        return dataManager.get(DESTINATIONZDW);
    }

    @Override
    /** Applies the given player interaction to this Entity. */
    public EnumActionResult applyPlayerInteraction(EntityPlayer player, Vec3d vec, @Nullable ItemStack stack,
            EnumHand hand)
    {
        if (interacter == null) interacter = new CraftInteractHandler(this);
        return interacter.applyPlayerInteraction(player, vec, stack, hand);
    }

    /** First layer of player interaction */
    @Override
    public boolean processInitialInteract(EntityPlayer player, @Nullable ItemStack stack, EnumHand hand)
    {
        return interacter.processInitialInteract(player, stack, hand);
    }

    @Override
    public boolean isPotionApplicable(PotionEffect par1PotionEffect)
    {
        return false;
    }

    @Override
    public void onUpdate()
    {
        if (net.minecraftforge.common.ForgeHooks.onLivingUpdate(this)) return;
        if (collider == null)
        {
            this.collider = new BlockEntityUpdater(this);
            this.collider.onSetPosition();
        }
        controller.doServerTick(world);
        this.prevPosY = this.posY;
        this.prevPosX = this.posX;
        this.prevPosZ = this.posZ;
        collider.onUpdate();
        int dy = (int) ((motionY) * 16);
        int dx = (int) ((motionX) * 16);
        int dz = (int) ((motionZ) * 16);
        toMoveX = controller.leftInputDown || controller.rightInputDown;
        toMoveZ = controller.backInputDown || controller.forwardInputDown;
        toMoveY = controller.upInputDown || controller.downInputDown;
        accelerate();
        if (toMoveY || toMoveX || toMoveZ)
        {
            doMotion();
        }
        else if (dx == dy && dy == dz && dz == 0 && !worldObj.isRemote)
        {
            setCalled(false);
            BlockPos pos = getPosition();
            setPosition(pos.getX() + 0.5, Math.round(posY), pos.getZ() + 0.5);
        }
        this.rotationYaw = 0;
        checkCollision();
        passengertime = hasPassenger ? 20 : passengertime - 1;
        n++;
    }

    public void passengerCheck()
    {
        List<Entity> list = worldObj.getEntitiesWithinAABBExcludingEntity(this, getEntityBoundingBox());
        if (list.size() > 0)
        {
            hasPassenger = true;
        }
        else
        {
            hasPassenger = false;
        }
    }

    public void readBlocks(NBTTagCompound nbt)
    {
        if (nbt.hasKey("Blocks"))
        {
            NBTTagCompound blockTag = nbt.getCompoundTag("Blocks");
            int sizeX = blockTag.getInteger("BlocksLengthX");
            int sizeZ = blockTag.getInteger("BlocksLengthZ");
            int sizeY = blockTag.getInteger("BlocksLengthY");
            if (sizeX == 0 || sizeZ == 0)
            {
                sizeX = sizeZ = nbt.getInteger("BlocksLength");
            }
            if (sizeY == 0) sizeY = 1;

            blocks = new ItemStack[sizeX][sizeY][sizeZ];
            tiles = new TileEntity[sizeX][sizeY][sizeZ];
            for (int i = 0; i < sizeX; i++)
                for (int k = 0; k < sizeY; k++)
                    for (int j = 0; j < sizeZ; j++)
                    {
                        int n = -1;
                        if (blockTag.hasKey("I" + i + "," + j))
                        {
                            n = blockTag.getInteger("I" + i + "," + j);
                        }
                        else if (blockTag.hasKey("I" + i + "," + k + "," + j))
                        {
                            n = blockTag.getInteger("I" + i + "," + k + "," + j);
                        }
                        if (n == -1) continue;
                        ItemStack b = new ItemStack(Item.getItemById(n), 1,
                                blockTag.getInteger("M" + i + "," + k + "," + j));
                        blocks[i][k][j] = b;
                        if (blockTag.hasKey("T" + i + "," + k + "," + j))
                        {
                            try
                            {
                                NBTTagCompound tag = blockTag.getCompoundTag("T" + i + "," + k + "," + j);
                                tiles[i][k][j] = IBlockEntity.BlockEntityFormer.makeTile(tag);
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                            }
                        }
                    }
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound nbt)
    {
        super.readEntityFromNBT(nbt);
        axis = nbt.getBoolean("axis");
        energy = nbt.getInteger("energy");
        if (nbt.hasKey("bounds"))
        {
            NBTTagCompound bounds = nbt.getCompoundTag("bounds");
            boundMin = new BlockPos(bounds.getDouble("minx"), bounds.getDouble("miny"), bounds.getDouble("minz"));
            boundMax = new BlockPos(bounds.getDouble("maxx"), bounds.getDouble("maxy"), bounds.getDouble("maxz"));
        }

        if (nbt.hasKey("higher")) id = new UUID(nbt.getLong("higher"), nbt.getLong("lower"));
        if (nbt.hasKey("ownerhigher")) owner = new UUID(nbt.getLong("ownerhigher"), nbt.getLong("ownerlower"));

        if (nbt.hasKey("replacement"))
        {
            NBTTagCompound held = nbt.getCompoundTag("replacement");
            setHeldItem(null, ItemStack.loadItemStackFromNBT(held));
        }
        readBlocks(nbt);
    }

    @Override
    public void readSpawnData(ByteBuf data)
    {
        PacketBuffer buff = new PacketBuffer(data);
        NBTTagCompound tag = new NBTTagCompound();
        try
        {
            tag = buff.readNBTTagCompoundFromBuffer();
            readEntityFromNBT(tag);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /** Will get destroyed next tick. */
    @Override
    public void setDead()
    {
        if (!worldObj.isRemote && !this.isDead)
        {
            IBlockEntity.BlockEntityFormer.RevertEntity(this);
        }
        super.setDead();
    }

    @Override
    public void setPosition(double x, double y, double z)
    {
        super.setPosition(x, y, z);
        if (collider != null) collider.onSetPosition();
    }

    /** @param dest
     *            the destinationFloor to set */
    public void setDestX(float dest)
    {
        dataManager.set(DESTINATIONXDW, Float.valueOf(dest));
        dataManager.set(DESTINATIONYDW, Float.valueOf((float) posY));
        dataManager.set(DESTINATIONZDW, Float.valueOf((float) posZ));
        setCalled(true);
    }

    /** @param dest
     *            the destinationFloor to set */
    public void setDestY(float dest)
    {
        dataManager.set(DESTINATIONYDW, Float.valueOf(dest));
        dataManager.set(DESTINATIONXDW, Float.valueOf((float) posX));
        dataManager.set(DESTINATIONZDW, Float.valueOf((float) posZ));
        setCalled(true);
    }

    /** @param dest
     *            the destinationFloor to set */
    public void setDestZ(float dest)
    {
        dataManager.set(DESTINATIONZDW, Float.valueOf(dest));
        dataManager.set(DESTINATIONYDW, Float.valueOf((float) posY));
        dataManager.set(DESTINATIONXDW, Float.valueOf((float) posX));
        setCalled(true);
    }

    public void writeBlocks(NBTTagCompound nbt)
    {
        if (blocks != null)
        {
            NBTTagCompound blocksTag = new NBTTagCompound();
            blocksTag.setInteger("BlocksLengthX", blocks.length);
            blocksTag.setInteger("BlocksLengthY", blocks[0].length);
            blocksTag.setInteger("BlocksLengthZ", blocks[0][0].length);
            int sizeX = blocks.length;
            int sizeY = blocks[0].length;
            int sizeZ = blocks[0][0].length;
            for (int i = 0; i < sizeX; i++)
            {
                for (int k = 0; k < sizeY; k++)
                {
                    for (int j = 0; j < sizeZ; j++)
                    {
                        ItemStack b = blocks[i][k][j];
                        if (b == null || b.getItem() == null) continue;
                        blocksTag.setInteger("I" + i + "," + k + "," + j, Item.getIdFromItem(b.getItem()));
                        blocksTag.setInteger("M" + i + "," + k + "," + j, b.getItemDamage());
                        try
                        {
                            if (tiles[i][k][j] != null)
                            {

                                NBTTagCompound tag = new NBTTagCompound();
                                tag = tiles[i][k][j].writeToNBT(tag);
                                blocksTag.setTag("T" + i + "," + k + "," + j, tag);
                            }
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            }
            nbt.setTag("Blocks", blocksTag);
        }
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound nbt)
    {
        super.writeEntityToNBT(nbt);
        nbt.setBoolean("axis", axis);

        NBTTagCompound vector = new NBTTagCompound();
        vector.setDouble("minx", boundMin.getX());
        vector.setDouble("miny", boundMin.getY());
        vector.setDouble("minz", boundMin.getZ());
        vector.setDouble("maxx", boundMax.getX());
        vector.setDouble("maxy", boundMax.getY());
        vector.setDouble("maxz", boundMax.getZ());
        nbt.setTag("bounds", vector);
        nbt.setInteger("energy", energy);
        if (owner != null)
        {
            nbt.setLong("ownerlower", owner.getLeastSignificantBits());
            nbt.setLong("ownerhigher", owner.getMostSignificantBits());
        }
        if (getHeldItem(null) != null)
        {
            NBTTagCompound held = new NBTTagCompound();
            getHeldItem(null).writeToNBT(held);
            nbt.setTag("replacement", held);
        }
        try
        {
            writeBlocks(nbt);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void writeSpawnData(ByteBuf data)
    {
        PacketBuffer buff = new PacketBuffer(data);
        NBTTagCompound tag = new NBTTagCompound();
        writeEntityToNBT(tag);
        buff.writeNBTTagCompoundToBuffer(tag);
    }

    @Override
    public Iterable<ItemStack> getArmorInventoryList()
    {
        return Lists.newArrayList();
    }

    @Override
    public ItemStack getItemStackFromSlot(EntityEquipmentSlot slotIn)
    {
        return null;
    }

    @Override
    public void setItemStackToSlot(EntityEquipmentSlot slotIn, ItemStack stack)
    {
    }

    @Override
    public ItemStack getHeldItem(EnumHand hand)
    {
        return null;
    }

    @Override
    public void setHeldItem(EnumHand hand, @Nullable ItemStack stack)
    {

    }

    @Override
    public EnumHandSide getPrimaryHand()
    {
        return EnumHandSide.LEFT;
    }

    @Override
    public void setBlocks(ItemStack[][][] blocks)
    {
        this.blocks = blocks;
    }

    @Override
    public ItemStack[][][] getBlocks()
    {
        return blocks;
    }

    @Override
    public void setTiles(TileEntity[][][] tiles)
    {
        this.tiles = tiles;
    }

    @Override
    public TileEntity[][][] getTiles()
    {
        return tiles;
    }

    @Override
    public BlockPos getMin()
    {
        return boundMin;
    }

    @Override
    public BlockPos getMax()
    {
        return boundMax;
    }

    @Override
    public void setMin(BlockPos pos)
    {
        this.boundMin = pos;
    }

    @Override
    public void setMax(BlockPos pos)
    {
        this.boundMax = pos;
    }

    @Override
    public void setFakeWorld(BlockEntityWorld world)
    {
        this.world = world;
    }

    public static EntityCraft getLiftFromUUID(final UUID liftID, World world)
    {
        EntityCraft ret = null;
        if (world instanceof BlockEntityWorld)
        {
            world = ((BlockEntityWorld) world).getWorld();
        }
        if (world instanceof WorldServer)
        {
            WorldServer worlds = (WorldServer) world;
            return (EntityCraft) worlds.getEntityFromUuid(liftID);
        }
        else
        {
            List<EntityCraft> entities = world.getEntities(EntityCraft.class, new Predicate<EntityCraft>()
            {
                @Override
                public boolean apply(EntityCraft input)
                {
                    return input.getUniqueID().equals(liftID);
                }
            });
            if (!entities.isEmpty()) return entities.get(0);
        }
        return ret;
    }

}
