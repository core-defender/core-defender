package lkd.namsic.coredefender.client;

import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.turret.BasicTurretBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.util.*;

@EventBusSubscriber(modid=CoreDefender.MOD_ID,value=Dist.CLIENT)
public final class TurretRangeRenderer {
    private TurretRangeRenderer(){}
    private static ClientLevel world;
    private static final Map<BlockPos,Long> shown=new HashMap<>();
    public static void show(BlockPos pos){var level=Minecraft.getInstance().level;if(level==null)return;if(world!=level){shown.clear();world=level;}shown.put(pos.immutable(),level.getGameTime()+300);}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.level!=world){shown.clear();world=mc.level;return;}
        shown.entrySet().removeIf(e->e.getValue()<world.getGameTime()||!(world.getBlockEntity(e.getKey()) instanceof BasicTurretBlockEntity));
        if(shown.isEmpty())return;
        var batch=WorldRenderBatch.begin(event);
        var out=batch.vertices();var matrix=batch.matrix();
        for(BlockPos pos:shown.keySet()){
            if (!(world.getBlockEntity(pos) instanceof BasicTurretBlockEntity turret)) continue;double range=turret.clientRange,angle=turret.kind()==2?turret.clientAngle:360,yaw=turret.renderYaw(0);
            double y=pos.getY()+.06,cx=pos.getX()+.5,cz=pos.getZ()+.5;
            for(int i=0;i<120;i++){
                double a=Math.toRadians(yaw-angle/2+angle*i/120),b=Math.toRadians(yaw-angle/2+angle*(i+1)/120);
                out.addVertex(matrix,(float)(cx-Math.sin(a)*range),(float)y,(float)(cz+Math.cos(a)*range)).setColor(85,230,195,220);
                out.addVertex(matrix,(float)(cx-Math.sin(b)*range),(float)y,(float)(cz+Math.cos(b)*range)).setColor(85,230,195,220);
                out.addVertex(matrix,(float)(cx-Math.sin(b)*(range-.08)),(float)y,(float)(cz+Math.cos(b)*(range-.08))).setColor(85,230,195,220);
                out.addVertex(matrix,(float)(cx-Math.sin(a)*(range-.08)),(float)y,(float)(cz+Math.cos(a)*(range-.08))).setColor(85,230,195,220);
            }
            if(angle<360)for(int side:new int[]{-1,1}) {
                double a=Math.toRadians(yaw+side*angle/2),dx=-Math.sin(a)*range,dz=Math.cos(a)*range;
                double nx=Math.cos(a)*.04,nz=Math.sin(a)*.04;
                out.addVertex(matrix,(float)(cx+nx),(float)y,(float)(cz+nz)).setColor(85,230,195,220);
                out.addVertex(matrix,(float)(cx+dx+nx),(float)y,(float)(cz+dz+nz)).setColor(85,230,195,220);
                out.addVertex(matrix,(float)(cx+dx-nx),(float)y,(float)(cz+dz-nz)).setColor(85,230,195,220);
                out.addVertex(matrix,(float)(cx-nx),(float)y,(float)(cz-nz)).setColor(85,230,195,220);
            }
        }
        batch.finish();
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.screen!=null||mc.level==null||!(mc.hitResult instanceof BlockHitResult hit)||!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof BasicTurretBlockEntity turret))return;
        String label=BasicTurretBlockEntity.STATUS[Math.clamp(turret.status,0,10)];if(turret.clientAmpWarning)label+=" · 증폭 소진";if(turret.outputBlocked)label+=" · 용기 회수 막힘";
        event.getGuiGraphics().drawCenteredString(mc.font,label,mc.getWindow().getGuiScaledWidth()/2,mc.getWindow().getGuiScaledHeight()/2+18,0xfff1cf88);
    }
}
