package thut.crafts.entity;

import java.util.List;
import java.util.UUID;

import javax.vecmath.Vector3f;

import com.google.common.collect.Lists;

import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import thut.api.entity.IMultiplePassengerEntity;
import thut.api.entity.blockentity.BlockEntityBase;
import thut.api.entity.blockentity.BlockEntityInteractHandler;
import thut.api.maths.Vector3;
import thut.api.network.PacketHandler;

public class EntityCraft extends BlockEntityBase implements IMultiplePassengerEntity
{
    @SuppressWarnings("unchecked")
    static final DataParameter<Seat>[]  SEAT       = new DataParameter[10];
    static final DataParameter<Integer> SEATCOUNT  = EntityDataManager.<Integer> createKey(EntityCraft.class,
            DataSerializers.VARINT);
    static final DataParameter<Integer> MAINSEATDW = EntityDataManager.<Integer> createKey(EntityCraft.class,
            DataSerializers.VARINT);

    static
    {
        for (int i = 0; i < SEAT.length; i++)
        {
            SEAT[i] = EntityDataManager.<Seat> createKey(EntityCraft.class, SEATSERIALIZER);
        }
    }

    public static boolean  ENERGYUSE  = false;
    public static int      ENERGYCOST = 100;

    public CraftController controller = new CraftController(this);
    int                    energy     = 0;
    public UUID            owner;

    public EntityCraft(World par1World)
    {
        super(par1World);
        this.ignoreFrustumCheck = true;
        this.hurtResistantTime = 0;
        this.isImmuneToFire = true;
    }

    public EntityCraft(World world, double x, double y, double z)
    {
        this(world);
        this.setPosition(x, y, z);
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
    }

    Seat getSeat(int index)
    {
        return this.dataManager.get(SEAT[index]);
    }

    @Override
    public void accelerate()
    {
        if (isServerWorld() && !consumePower())
        {
            toMoveY = toMoveX = toMoveZ = false;
        }
        toMoveX = controller.leftInputDown || controller.rightInputDown;
        toMoveZ = controller.backInputDown || controller.forwardInputDown;
        toMoveY = controller.upInputDown || controller.downInputDown;
        float destY = toMoveY ? (controller.upInputDown ? 30 : -30) : 0;
        float destX = toMoveX ? (controller.leftInputDown ? 30 : -30) : 0;
        float destZ = toMoveZ ? (controller.forwardInputDown ? 30 : -30) : 0;
        toMoveY = toMoveX = toMoveZ = false;

        if (destX == destY && destY == destZ && destZ == 0)
        {
            motionZ *= 0.5;
            motionY *= 0.5;
            motionZ *= 0.5;
            return;
        }

        Vector3 dest = Vector3.getNewVector().set(destX, destY, destZ);
        Seat seat = null;
        for (int i = 0; i < getSeatCount(); i++)
        {
            if (!getSeat(i).entityId.equals(Seat.BLANK))
            {
                seat = getSeat(i);
                break;
            }
        }
        if (seat != null)
        {
            Vector3 rel = Vector3.getNewVector().set(this).addTo(seat.seat.x, seat.seat.y, seat.seat.z);
            BlockPos pos = rel.getPos();
            IBlockState block = getFakeWorld().getBlockState(pos);
            switch (block.getValue(BlockStairs.FACING))
            {
            case DOWN:
                break;
            case EAST:
                dest = dest.rotateAboutAngles(0, -Math.PI / 2, Vector3.getNewVector(), Vector3.getNewVector());
                break;
            case NORTH:
                break;
            case SOUTH:
                dest = dest.rotateAboutAngles(0, Math.PI, Vector3.getNewVector(), Vector3.getNewVector());
                break;
            case UP:
                break;
            case WEST:
                dest = dest.rotateAboutAngles(0, Math.PI / 2, Vector3.getNewVector(), Vector3.getNewVector());
                break;
            default:
                break;

            }
        }

        float f = (float) Math.sqrt(destX * destX + destZ * destZ);
        // Apply rotationYaw to destination
        if (controller.forwardInputDown)
        {
            destX = MathHelper.sin(-rotationYaw * 0.017453292F) * f;
            destZ = MathHelper.cos(rotationYaw * 0.017453292F) * f;
        }
        else if (controller.backInputDown)
        {
            destX = -MathHelper.sin(-rotationYaw * 0.017453292F) * f;
            destZ = -MathHelper.cos(rotationYaw * 0.017453292F) * f;
        }
        else if (controller.leftInputDown)
        {
            destX = MathHelper.cos(-rotationYaw * 0.017453292F) * f;
            destZ = MathHelper.sin(rotationYaw * 0.017453292F) * f;
        }
        else if (controller.rightInputDown)
        {
            destX = -MathHelper.cos(-rotationYaw * 0.017453292F) * f;
            destZ = -MathHelper.sin(rotationYaw * 0.017453292F) * f;
        }

        destX += posX;
        destY += posY;
        destZ += posZ;

        if (destY != posY)
        {
            double dy = getSpeed(posY, destY, motionY, speedUp, speedDown);
            motionY = dy;
            toMoveY = true;
        }
        else motionY *= 0.5;
        if (destX != posX)
        {
            double dx = getSpeed(posX, destX, motionX, speedHoriz, speedHoriz);
            motionX = dx;
            toMoveX = true;
        }
        else motionX *= 0.5;
        if (destZ != posZ)
        {
            double dz = getSpeed(posZ, destZ, motionZ, speedHoriz, speedHoriz);
            motionZ = dz;
            toMoveZ = true;
        }
        else motionZ *= 0.5;
    }

    @Override
    public void updatePassenger(Entity passenger)
    {
        if (this.isPassenger(passenger))
        {
            if (passenger.isSneaking())
            {
                passenger.stopRiding();
            }
            IMultiplePassengerEntity.MultiplePassengerManager.managePassenger(passenger, this);
        }
    }

    @Override
    protected void removePassenger(Entity passenger)
    {
        super.removePassenger(passenger);
        if (!world.isRemote) for (int i = 0; i < getSeatCount(); i++)
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

    private boolean consumePower()
    {
        if (!ENERGYUSE) return true;
        boolean power = false;
        Vector3 bounds = Vector3.getNewVector().set(boundMax.subtract(boundMin));
        double volume = bounds.x * bounds.y * bounds.z;
        float speed = 10;
        double energyCost = Math.abs(speed) * ENERGYCOST * volume * 0.01;
        energyCost = Math.max(energyCost, 1);
        power = (energy = (int) (energy - energyCost)) > 0;
        if (energy < 0) energy = 0;
        MinecraftForge.EVENT_BUS.post(new EventCraftConsumePower(this, (long) energyCost));
        if (!power)
        {
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

    @Override
    public void doMotion()
    {
        this.move(MoverType.SELF, motionX, motionY, motionZ);
    }

    @Override
    protected void entityInit()
    {
        super.entityInit();
        this.dataManager.register(MAINSEATDW, Integer.valueOf(-1));
        for (int i = 0; i < 10; i++)
            dataManager.register(SEAT[i], new Seat(new Vector3f(), null));
        dataManager.register(SEATCOUNT, 0);
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

    @Override
    public void readEntityFromNBT(CompoundNBT nbt)
    {
        super.readEntityFromNBT(nbt);
        energy = nbt.getInteger("energy");
        if (nbt.hasKey("seats"))
        {
            ListNBT seatsList = nbt.getTagList("seats", 10);
            for (int i = 0; i < seatsList.size(); ++i)
            {
                CompoundNBT nbt1 = seatsList.getCompound(i);
                Seat seat = Seat.readFromNBT(nbt1);
                this.getSeat(i).seat = seat.seat;
                this.getSeat(i).entityId = seat.entityId;
            }
        }
    }

    @Override
    public void writeEntityToNBT(CompoundNBT nbt)
    {
        super.writeEntityToNBT(nbt);
        nbt.setInteger("energy", energy);
        ListNBT seats = new ListNBT();
        for (int i = 0; i < getSeatCount(); i++)
        {
            CompoundNBT tag1 = new CompoundNBT();
            getSeat(i).writeToNBT(tag1);
            seats.appendTag(tag1);
        }
        nbt.setTag("seats", seats);
    }

    @Override
    protected boolean checkAccelerationConditions()
    {
        return consumePower();
    }

    @Override
    protected BlockEntityInteractHandler createInteractHandler()
    {
        return new CraftInteractHandler(this);
    }

    @Override
    protected void onGridAlign()
    {
        BlockPos pos = getPosition();
        setPosition(pos.getX() + 0.5, Math.round(posY), pos.getZ() + 0.5);
        PacketHandler.sendEntityUpdate(this);
    }

    @Override
    protected void preColliderTick()
    {
        controller.doServerTick(getFakeWorld());
    }
}
