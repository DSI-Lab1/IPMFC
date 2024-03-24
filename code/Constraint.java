import java.util.*;

public class Constraint {
    int[] target;
    String relations;
    int mingap;
    int maxgap;

    public Constraint() {
        this.target = new int[]{};
        this.relations = "";
        this.mingap = -1;
        this.maxgap = -1;
    }

    public Constraint(int[] target, String relations, int[] gap) {
        this.target = target;
        this.relations = relations;
        this.mingap = gap[0];
        this.maxgap = gap[1];
    }

    @Override
    public String toString() {
        return "Target: " + Arrays.toString(target) + ", relations: " + relations + ", mingap: " + mingap + ", maxgap: " + maxgap;
    }
}
