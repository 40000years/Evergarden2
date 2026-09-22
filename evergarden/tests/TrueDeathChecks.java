import com.example.voidscape.dungeon.TrueDeathProgress;

public final class TrueDeathChecks {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        TrueDeathProgress state=new TrueDeathProgress(0,0,false,false);
        for(int level=1;level<=5;level++) {
            for(int hit=1;hit<=10;hit++)state=TrueDeathProgress.hit(state.hits(),state.level(),10,5);
            check(state.hits()==0&&state.level()==level&&state.triggered(),"stack "+level);
            check(state.finalDeath()==(level==5),"final death only at stack V");
        }
        state=TrueDeathProgress.hit(state.hits(),state.level(),10,5);
        check(state.level()==5&&!state.finalDeath(),"level is capped and only a completed cycle triggers death");
        System.out.println("PASS: True Death reaches I-V every 10 hits and final death triggers only at V");
    }
}
