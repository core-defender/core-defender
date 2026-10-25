package lkd.namsic.coredefender.turret;

import lkd.namsic.coredefender.registry.ModTurrets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public class TurretMenu extends AbstractContainerMenu {
    public final BasicTurretBlockEntity turret;
    public final ContainerData data;
    // Minecraft owns this level; borrowers must not close it.
    @SuppressWarnings("resource")
    public TurretMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf) { this(id,inv,(BasicTurretBlockEntity)inv.player.level().getBlockEntity(buf.readBlockPos())); }
    public TurretMenu(int id,Inventory inv,BasicTurretBlockEntity turret) {
        super(ModTurrets.MENU.get(),id);this.turret=turret;
        // Minecraft owns this level; borrowers must not close it.
        //noinspection resource
        this.data=inv.player.level().isClientSide?new SimpleContainerData(46):new ContainerData() {
            public int get(int i){return turret.data(i);}public void set(int i,int value){}public int getCount(){return 46;}
        };
        this.addDataSlots(this.data);
        for(int i=0;i<9;i++)this.addSlot(new SlotItemHandler(turret.inventory,i,14+(i%3)*18,48+(i/3)*18) {
            @Override public boolean isActive(){return TurretMenu.this.data.get(0)!=2;}
        });
        this.addSlot(new SlotItemHandler(turret.inventory,9,14,122));this.addSlot(new SlotItemHandler(turret.inventory,10,34,122));
        this.addSlot(new SlotItemHandler(turret.inventory,11,65,122) {@Override public boolean isActive(){return TurretMenu.this.data.get(0)==1;}});
        this.addSlot(new SlotItemHandler(turret.inventory,12,86,122) {@Override public boolean isActive(){return TurretMenu.this.data.get(0)==2;}});
        this.addSlot(new SlotItemHandler(turret.inventory,13,110,122) {@Override public boolean isActive(){return TurretMenu.this.data.get(0)==2;}@Override public boolean mayPlace(ItemStack s){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)this.addSlot(new Slot(inv,col+row*9+9,14+col*18,182+row*18));
        for(int col=0;col<9;col++)this.addSlot(new Slot(inv,col,14+col*18,240));
    }
    // Minecraft owns this level; borrowers must not close it.
    @SuppressWarnings("resource")
    @Override public boolean stillValid(Player player){return this.turret!=null && !this.turret.isRemoved() && player.level()==this.turret.getLevel() && player.distanceToSqr(this.turret.getBlockPos().getX()+.5,this.turret.getBlockPos().getY()+.5,this.turret.getBlockPos().getZ()+.5)<=64;}
    // Minecraft owns this level; borrowers must not close it.
    @SuppressWarnings("resource")
    @Override public boolean clickMenuButton(Player player,int id){if(!this.stillValid(player))return false;if(!player.level().isClientSide)this.turret.action(id);return true;}
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(!this.stillValid(player)||index<0||index>=this.slots.size())return ItemStack.EMPTY;
        Slot slot=this.slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),copy=stack.copy();
        if(index<14) {if(!this.moveItemStackTo(stack,14,this.slots.size(),true))return ItemStack.EMPTY;}
        else if(!this.moveItemStackTo(stack,0,13,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();return copy;
    }
}
