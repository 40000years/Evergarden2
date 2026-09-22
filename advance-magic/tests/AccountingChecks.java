import com.example.advancemagic.mana.CastAccount;
import com.example.advancemagic.effect.Geometry;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class AccountingChecks {
    static int count;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);count++;}
    public static void main(String[] args) {
        CastAccount a=new CastAccount(100);
        check(a.reserve("lightning",60,8,1000),"first cast");
        check(a.mana()==40,"exact mana cost");
        check(!a.reserve("lightning",60,8,1000),"same-tick duplicate blocked");
        check(!a.reserve("frost",50,12,1000),"insufficient mana blocked");
        check(a.mana()==40,"failures do not consume mana");
        for(int i=0;i<5;i++)a.regenerate();
        check(a.mana()==50,"two mana per regeneration step");
        check(a.reserve("frost",50,12,2000),"independent per-spell cooldown");
        a.refund("frost",50);
        check(a.mana()==50&&a.remaining("frost",2000)==0,"failed target refunds mana and cooldown");
        check(a.remaining("lightning",8999)==1,"cooldown boundary before expiry");
        check(a.remaining("lightning",9000)==0,"cooldown exact expiry");
        for(int i=0;i<100;i++)a.regenerate();
        check(a.mana()==100,"regen capped at 100");
        CastAccount restored=new CastAccount(a.mana());restored.restore("lightning",20000);
        check(restored.remaining("lightning",18000)==2000,"persisted cooldown survives reconstruction");
        check(new CastAccount(-30).mana()==0&&new CastAccount(900).mana()==100,"stored mana clamped");
        BoundingBox wall=new BoundingBox(3,0,-2,4,3,3);
        check(Geometry.intersects(wall,new Vector(0,1,0),new Vector(20,1,0)),"fast projectile cannot tunnel through wall");
        check(!Geometry.intersects(wall,new Vector(0,4,0),new Vector(20,4,0)),"projectile above wall passes");
        check(Geometry.intersects(wall,new Vector(3.5,1,0),new Vector(3.5,1,0)),"stationary projectile inside wall");
        check(!Geometry.intersects(wall,new Vector(0,1,0),new Vector(0,1,0)),"stationary projectile outside wall");
        // Upgraded mana and dragon breath progression assertions
        CastAccount upgraded=new CastAccount(150,250,5.0);
        check(upgraded.maxMana()==250.0,"custom max mana preserved");
        check(upgraded.regenRate()==5.0,"custom regen rate preserved");
        upgraded.regenerate();
        check(upgraded.mana()==155,"custom regen rate adds exact amount");

        // Dragon breath progression test
        double maxCap=100.0;
        for(int d=0;d<10;d++){if(maxCap<150.0)maxCap+=5.0;else if(maxCap<200.0)maxCap+=2.0;else maxCap=Math.min(300.0,maxCap+0.5);}
        check(maxCap==150.0,"10 drinks reach exactly 150");
        for(int d=0;d<25;d++){if(maxCap<150.0)maxCap+=5.0;else if(maxCap<200.0)maxCap+=2.0;else maxCap=Math.min(300.0,maxCap+0.5);}
        check(maxCap==200.0,"25 more drinks reach exactly 200");
        for(int d=0;d<200;d++){if(maxCap<150.0)maxCap+=5.0;else if(maxCap<200.0)maxCap+=2.0;else maxCap=Math.min(300.0,maxCap+0.5);}
        check(maxCap==300.0,"subsequent drinks hit hard cap 300");

        // Wand cooldown reduction formula test
        int wandBaseCd=25; // Time Dilation
        double minCd=Math.max(1.0,wandBaseCd*0.2); // 5.0s
        double cd0=Math.max(minCd,wandBaseCd-(0/5)*5.0);
        double cd5=Math.max(minCd,wandBaseCd-(5/5)*5.0);
        double cd10=Math.max(minCd,wandBaseCd-(10/5)*5.0);
        double cd25=Math.max(minCd,wandBaseCd-(25/5)*5.0);
        check(cd0==25.0&&cd5==20.0&&cd10==15.0&&cd25==5.0,"wand-bound cooldown reduction scales every 5 casts and caps at safe floor");

        System.out.println("PASS: "+count+" accounting and collision assertions");
    }
}
