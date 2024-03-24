import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        String split = " |,";
        String filenames = "input/asl";
        double percents = 0.1;
        AlgoIPMFC algo = new AlgoIPMFC();
        Constraint target = new Constraint(new int[]{}, "", new int[]{-1, -1});
        algo.setParameters(filenames, filenames + "-output.txt", percents, target, split);
        algo.runAlgo();
    }
}