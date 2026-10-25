package lkd.namsic.coredefender.turret;

import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.ai.attributes.Attributes;

import lkd.namsic.coredefender.registry.*;
import lkd.namsic.coredefender.wave.WaveMobs;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.world.level.material.Fluids;

@GameTestHolder("coredefender")
@PrefixGameTestTemplate(false)
// Registered entry point: invoked by the game registry, scripts, or annotation discovery.
@SuppressWarnings("unused")
public class TurretGameTests {
    private Husk victim(GameTestHelper h,int x,int z) {
        Husk mob=h.spawn(EntityType.HUSK,new BlockPos(x,2,z));mob.setNoAi(true);mob.setNoGravity(true);mob.addTag(WaveMobs.TAG);return mob;
    }
    private BasicTurretBlockEntity turret(GameTestHelper h,int kind) {
        BlockPos pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlockAndUpdate(pos,(kind==0?ModBlocks.BASIC_TURRET:kind==1?ModBlocks.STONE_TURRET:ModBlocks.FLAME_TURRET).get().defaultBlockState());
        return (BasicTurretBlockEntity)h.getLevel().getBlockEntity(pos);
    }
    @GameTest(template="empty",timeoutTicks=40)
    // Registered entry point: invoked by the game registry, scripts, or annotation discovery.
    @SuppressWarnings("unused")
    public void damageHasNoKnockbackOrInvulnerabilitySharing(GameTestHelper h) {
        Husk mob=this.victim(h,3,3);var t=this.turret(h,0);Vec3 before=new Vec3(.12,0,-.08);mob.setDeltaMovement(before);mob.invulnerableTime=17;
        TurretCombat.damage(h.getLevel(),mob,h.getLevel().damageSources().generic(),4,t.getBlockPos(),t.identity,null);
        TurretCombat.damage(h.getLevel(),mob,h.getLevel().damageSources().generic(),4,t.getBlockPos(),t.identity,null);
        h.assertTrue(mob.getHealth()==12,"Two independent turret hits must both apply");
        h.assertTrue(mob.getDeltaMovement().equals(before),"Turret damage must preserve all velocity components");
        h.assertTrue(mob.invulnerableTime==17,"Other attackers' immunity window must survive");
        h.assertTrue(Math.abs(t.actualDps()-.8)<.001,"Actual DPS uses actual HP lost");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    // Registered entry point: invoked by the game registry, scripts, or annotation discovery.
    @SuppressWarnings("unused")
    public void arrowsUseFixedDamageAndIgnoreNonEnemies(GameTestHelper h) {
        Husk mob=this.victim(h,4,3);mob.setHealth(20);var t=this.turret(h,0);
        var friend=h.spawn(EntityType.COW,new BlockPos(2,2,3));friend.setNoAi(true);friend.setNoGravity(true);
        Vec3 start=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1,2,3))).add(0,.9,0);
        Arrow arrow=new Arrow(h.getLevel(),start.x,start.y,start.z,new ItemStack(Items.ARROW),null);
        arrow.addTag(BasicTurretBlockEntity.ARROW_TAG);arrow.getPersistentData().putDouble("cd_damage",4);arrow.getPersistentData().putLong("cd_origin",t.getBlockPos().asLong());arrow.getPersistentData().putUUID("cd_turret",t.identity);
        arrow.shoot(1,0,0,2.4F,0);h.getLevel().addFreshEntity(arrow);
        float expected=20-CombatRules.getDamageAfterAbsorb(mob,4,h.getLevel().damageSources().arrow(arrow,null),mob.getArmorValue(),(float)mob.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        h.runAfterDelay(8,()->{h.assertTrue(Math.abs(mob.getHealth()-expected)<.001,"Arrow damage: expected="+expected+", actual="+mob.getHealth());h.assertTrue(friend.getHealth()==friend.getMaxHealth(),"Friendly entity must not absorb or take the arrow");h.assertTrue(Math.abs(mob.getDeltaMovement().x)<.00001,"Arrow must not impart velocity");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=40)
    // Registered entry point: invoked by the game registry, scripts, or annotation discovery.
    @SuppressWarnings("unused")
    public void automationAndPortableState(GameTestHelper h) {
        var t=this.turret(h,1);var port=t.itemPort(Direction.NORTH);ItemStack arrows=new ItemStack(Items.COBBLESTONE,32);
        h.assertTrue(port.insertItem(0,arrows,false).getCount()==32,"New ports must be disabled");
        t.faces[Direction.NORTH.ordinal()]=3;t.limits[0]=16;
        h.assertTrue(port.insertItem(0,arrows,false).getCount()==16,"Input must honor total target stock");
        h.assertTrue(port.insertItem(1,arrows,false).getCount()==32,"Other ammo slots must share that limit");
        h.assertTrue(port.extractItem(0,64,false).isEmpty(),"Ammo must never auto-extract");
        t.fuelShots=7;t.ampShots=12;t.ampType=2;t.lockTarget=false;t.priority=3;
        CompoundTag saved=t.portableData();t.inventory.setStackInSlot(0,ItemStack.EMPTY);t.restore(saved);
        h.assertTrue(t.arrowCount()==16&&t.fuelShots==7&&t.ampShots==12&&t.priority==3&&!t.lockTarget,"Portable state must retain resources and settings");
        var f=new BasicTurretBlockEntity(h.absolutePos(new BlockPos(1,1,2)),ModBlocks.FLAME_TURRET.get().defaultBlockState());f.setLevel(h.getLevel());f.faces[Direction.UP.ordinal()]=1;f.limits[4]=500;
        var fluids=f.fluidPort(Direction.UP);
        h.assertTrue(fluids.fill(new FluidStack(Fluids.LAVA,1000),IFluidHandler.FluidAction.EXECUTE)==500,"Pipe fill must support partial stock limit");
        h.assertTrue(fluids.drain(1000,IFluidHandler.FluidAction.EXECUTE).isEmpty(),"Automatic fluid extraction must be disabled");
        h.assertTrue(f.itemPort(Direction.UP).insertItem(12,new ItemStack(Items.LAVA_BUCKET),false).getCount()==1,"A hopper bucket must not exceed the same fluid stock limit");
        h.assertTrue(!f.inventory.isItemValid(12,new ItemStack(Items.WATER_BUCKET)),"Unrelated fluids must not jam the container slot");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    // Registered entry point: invoked by the game registry, scripts, or annotation discovery.
    @SuppressWarnings("unused")
    public void potionSourceSurvivesSaveAndTick(GameTestHelper h) {
        var t=this.turret(h,0);var mob=this.victim(h,3,3);
        MobEffectInstance effect=new MobEffectInstance(MobEffects.HEAL,1,0);
        ((TurretEffect)effect).cd$source(new TurretEffect.Source(t.getBlockPos(),t.identity));
        MobEffectInstance loaded=Objects.requireNonNull(MobEffectInstance.load((CompoundTag)effect.save()));
        h.assertTrue(((TurretEffect)loaded).cd$source()!=null,"Potion source must survive NBT");
        mob.addEffect(loaded);Vec3 before=mob.getDeltaMovement();loaded.tick(mob,()->{});
        h.assertTrue(mob.getHealth()==14,"Healing arrow effect must damage undead using vanilla rules");
        h.assertTrue(mob.getDeltaMovement().equals(before),"Deferred potion damage must not cause knockback");h.succeed();
    }
    private BasicTurretBlockEntity enabledTurret(GameTestHelper h,int kind) {
        var original=this.turret(h,kind);
        // GameTestServer creates only vanilla dimensions. Override only the operating-area gate.
        var t=new BasicTurretBlockEntity(original.getBlockPos(),original.getBlockState()) {
            @Override protected boolean operatingArea(ServerLevel level){return true;}
        };
        h.getLevel().setBlockEntity(t);t.priority=0;return t;
    }
    private Arrow launch(GameTestHelper h,BasicTurretBlockEntity t,int bounce) {
        Vec3 start=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1,2,3))).add(0,.9,0);
        Arrow arrow=new Arrow(h.getLevel(),start.x,start.y,start.z,new ItemStack(Items.ARROW),null);
        arrow.addTag(BasicTurretBlockEntity.ARROW_TAG);var tag=arrow.getPersistentData();tag.putDouble("cd_damage",4);tag.putInt("cd_pierce",1);tag.putInt("cd_ricochet",bounce);tag.putLong("cd_origin",t.getBlockPos().asLong());tag.putUUID("cd_turret",t.identity);
        arrow.shoot(1,0,0,2.4F,0);h.getLevel().addFreshEntity(arrow);return arrow;
    }
    @GameTest(template="empty",timeoutTicks=40)
    // Registered entry point: invoked by the game registry, scripts, or annotation discovery.
    @SuppressWarnings("unused")
    public void ricochetOnlyFollowsFinalPiercingHit(GameTestHelper h) {
        var t=this.turret(h,0);var first=this.victim(h,3,3);var last=this.victim(h,5,3);var next=this.victim(h,6,5);var shot=this.launch(h,t,1);
        h.runAfterDelay(12,()->{h.assertTrue(first.getHealth()<20&&last.getHealth()<20&&next.getHealth()<20,"Pierce/ricochet health="+first.getHealth()+"/"+last.getHealth()+"/"+next.getHealth()+" pos="+shot.position()+" tag="+shot.getPersistentData());h.assertTrue(first.getHealth()<last.getHealth()&&last.getHealth()<next.getHealth(),"Damage must attenuate on each stage");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=40)
    // Registered entry point: invoked by the game registry, scripts, or annotation discovery.
    @SuppressWarnings("unused")
    public void missingFinalPierceDoesNotRicochet(GameTestHelper h) {
        var t=this.turret(h,0);var first=this.victim(h,3,3);var beside=this.victim(h,4,5);this.launch(h,t,2);
        h.runAfterDelay(12,()->{h.assertTrue(first.getHealth()<20,"First pierce must hit");h.assertTrue(beside.getHealth()==20,"An unfulfilled final pierce must not start ricochet");h.succeed();});
    }
    @GameTest(template="empty",batch="stoneTurretConsumesOneRockAndOneFuelCharge")
    // Registered entry point: invoked by the game registry, scripts, or annotation discovery.
    @SuppressWarnings("unused")
    public void stoneTurretConsumesOneRockAndOneFuelCharge(GameTestHelper h) {
        var t=this.enabledTurret(h,1);var mob=this.victim(h,4,3);
        t.inventory.setStackInSlot(0,new ItemStack(Items.COBBLESTONE));t.inventory.setStackInSlot(8,new ItemStack(Items.COAL));t.inventory.setStackInSlot(11,new ItemStack(Items.GUNPOWDER));
        h.runAfterDelay(75,()->{h.assertTrue(t.arrowCount()==0&&t.fuelShots==7&&t.ampShots==15,"One shot must consume one stone, one of eight coal charges and one of sixteen amp charges; actual="+t.arrowCount()+"/"+t.fuelShots+"/"+t.ampShots+" status="+t.status);h.assertTrue(mob.getHealth()<20,"Ballistic stone must actually hit and damage");h.succeed();});
    }
    @GameTest(template="empty",batch="flameBurstConsumesPerTickNotPerEnemy")
    // Registered entry point: invoked by the game registry, scripts, or annotation discovery.
    @SuppressWarnings("unused")
    public void flameBurstConsumesPerTickNotPerEnemy(GameTestHelper h) {
        var t=this.enabledTurret(h,2);var first=this.victim(h,3,2);var second=this.victim(h,3,3);
        // The isolated server has no Create Additions; direct test fill supplies exactly one burst budget.
        t.oil.setFluid(new FluidStack(Fluids.WATER,20));t.lava.setFluid(new FluidStack(Fluids.LAVA,1000));
        h.runAfterDelay(45,()->{h.assertTrue(t.oil.isEmpty()&&t.lava.getFluidAmount()==990,"Ten active ticks must consume 20mB oil and 10mB lava regardless of victims");h.assertTrue(first.getHealth()<20&&second.getHealth()<20,"Fan must damage multiple enemies");h.assertTrue(Math.abs(first.getDeltaMovement().x)<.00001&&Math.abs(second.getDeltaMovement().x)<.00001,"Flame and burn must not impart velocity");h.succeed();});
    }
    @GameTest(template="empty",batch="multishotConsumesActualDistinctShots")
    // Registered entry point: invoked by the game registry, scripts, or annotation discovery.
    @SuppressWarnings("unused")
    public void multishotConsumesActualDistinctShots(GameTestHelper h) {
        var t=this.enabledTurret(h,0);this.victim(h,4,3);
        t.inventory.setStackInSlot(0,new ItemStack(Items.ARROW,64));
        t.inventory.setStackInSlot(9,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("coredefender:module_multishot_3"))));
        h.succeedWhen(()->{h.assertTrue(t.getLastShot()>0,"Wait for shot");h.assertTrue(t.arrowCount()==63,"One target must consume exactly one arrow despite multishot III");});
    }
}
