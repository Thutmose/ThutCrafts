package thut.crafts.entity;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

public class CraftController
{
    public boolean    leftInputDown    = false;
    public boolean    rightInputDown   = false;
    public boolean    forwardInputDown = false;
    public boolean    backInputDown    = false;
    public boolean    leftRotateDown   = false;
    public boolean    rightRotateDown  = false;
    public boolean    upInputDown      = false;
    public boolean    downInputDown    = false;

    final EntityCraft entity;

    public CraftController(EntityCraft entityCraft)
    {
        this.entity = entityCraft;
    }

    public void doServerTick(World world)
    {
        if (!entity.isBeingRidden()) return;
        if (leftRotateDown)
        {
            for (Entity passenger : entity.getPassengers())
            {
                passenger.rotationYaw -= 5;
            }
            entity.rotationYaw -= 5;
        }
        if (rightRotateDown)
        {
            for (Entity passenger : entity.getPassengers())
            {
                passenger.rotationYaw += 5;
            }
            entity.rotationYaw += 5;
        }
    }
}
