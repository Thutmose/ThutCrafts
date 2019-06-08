package thut.crafts.entity;

public class EventCraftConsumePower extends Event
{
    public final EntityCraft craft;
    public final long toConsume;

    public EventCraftConsumePower(EntityCraft lift, long toConsume)
    {
        this.craft = lift;
        this.toConsume = toConsume;
    }
}
