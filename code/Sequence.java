import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;

// 记录一个序列 <label set, relation set>
public class Sequence {
    // mining process
    ArrayList<Integer> labels;                              // 记录sequence的所有label
    String relations;                                       // 记录sequence的relations
    Map<Integer, ArrayList<ArrayList<Integer>>> positions;  // sid -> position
    BitSet sids;                                            // 记录sequence的所有sid

    // target constraint
    int prefix;                                                  // 记录下一个需要匹配的target数组中label的位置
    // relations constraint
    Map<Integer, ArrayList<ArrayList<Integer>>> targetPositions; // sid -> target position
    public Sequence(ArrayList<Integer> labels, String relations, Map<Integer, ArrayList<ArrayList<Integer>>> positions, BitSet sids, int prefix, Map<Integer, ArrayList<ArrayList<Integer>>> targetPositions) {
        this.labels = labels;
        this.relations = relations;
        this.positions = positions;
        this.sids = sids;
        this.prefix = prefix;
        this.targetPositions = targetPositions;
    }
}
