import com.example.voidscape.item.VaultLootTable;
import java.util.Arrays;

public final class LootChecks {
    public static void main(String[] args) {
        int[] counts=new int[7];
        for(int ticket=0;ticket<10000;ticket++)counts[VaultLootTable.reward(ticket).ordinal()]++;
        if(!Arrays.equals(counts,new int[]{1500,1200,700,500,6000,50,50}))
            throw new AssertionError("Incorrect reward odds: "+Arrays.toString(counts));
        for(int invalid:new int[]{-1,10000}) {
            try {VaultLootTable.reward(invalid);throw new AssertionError("Invalid ticket accepted");}
            catch(IllegalArgumentException expected) {}
        }
        System.out.println("PASS: all 10,000 vault tickets tested with new balanced odds");
    }
}
