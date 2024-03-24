import java.io.*;
import java.util.*;

public class AlgoIPMFC {
    //********** 程序参数 **********//
    // 输入文件
    String input;

    // 输出文件
    String output;

    // 支持度阈值
    double minsup = 0;
    double percent = 0;

    // 目标序列
    // 这里的Target只关心label，不关心relations，以后可能会关心relations
    Constraint constraint;
    boolean isTarget;
    boolean isRelation;
    boolean isGap;

    String split; //数据库分隔符

    // record for calculate program run time
    double startTimestamp = 0;

    double endTimestamp = 0;

    long totalCount = 0; // 记录一共多少频繁序列

    long totalTargetSeq = 0; // 记录一共多少序列

    long SeqLen1Count = 0; //记录多少长度为1的频繁序列

    long maxSeqLen = 0; // 记录最长的频繁序列长度

    BufferedWriter write = null;

    //**********  读取的数据结构 **********//
    // 数据库
    Map<Integer, ArrayList<Event>> database = new HashMap<>();

    // label -> Sequence: 记录所有长度为1label的sequence
    Map<Integer, Sequence> label2seq = new HashMap<>();

    // label -> support: 记录label的支持度
    Map<Integer, Integer> label2sup = new HashMap<>();

    //*********  挖掘过程的数据结构  **************//
    // 所有长度为1的频繁序列
    ArrayList<Integer> one = new ArrayList<>();

    // 设置程序参数
    public void setParameters(String input, String output, double percent, Constraint constraint, String split){
        this.input = input + ".txt";
        this.output = output;
        this.percent = percent;
        this.constraint = constraint;
        this.split = split;
        this.isTarget = constraint.target.length > 0;
        // relations的长度小于等于1或者不等于label的个数*(label的个数-1)/2，表示不考虑relations
        int len = constraint.target.length;
        if(constraint.target.length <= 1 || constraint.relations.length() != (len*(len-1))/2 ) {
            this.isRelation = false;
        } else {
            this.isRelation = true;
        }
        // 将mingap或者maxgap设置为-1，表示不考虑gap
        this.isGap = constraint.mingap >= 0  && constraint.maxgap >= 0;
    }

    // 主程序
    public void runAlgo() throws IOException {
        startTimestamp = System.currentTimeMillis();

        write = new BufferedWriter(new FileWriter(output));

        // 读书数据库，填充database, label2Line, label2sup
        readDatabase();
        minsup = totalTargetSeq * percent;

        // 获得所有长度为1的频繁event label, 放入one中
        calculateAllL1Seq();
        SeqLen1Count = label2seq.size();

        // 扩展序列 1 -> n
        for(Integer label1 : one){
            for(Integer label2: one){
                findPattern(label2seq.get(label1), label2, 1);
            }
        }

        write.close();
        endTimestamp = System.currentTimeMillis();
        printStatistics();
    }

    // len表示seq的长度，seq表示当前序列， label表示需要扩展的event label
    private void findPattern(Sequence seq, int label, int len) throws IOException {
        Sequence seq1 = label2seq.get(label);
        BitSet lines = retainLine(seq, seq1);  //计算seq和label共同存在的行
        // 如何交集的行数小于minsup，直接return
        if(lines.cardinality() < minsup) {
            return;
        }
        Map<String, Map<Integer, ArrayList<ArrayList<Integer>>>> rela2position = new HashMap<>();       // relation -> line -> position
        Map<String, Map<Integer, ArrayList<ArrayList<Integer>>>> rela2targetPosition = new HashMap<>(); // relation -> line -> position
        Map<String, BitSet> rela2sids = new HashMap<>();
        // 构建长度为len的sequence
        int sid = lines.nextSetBit(0);
        while(sid != -1){
            ArrayList<Event> line = database.get(sid);
            ArrayList<ArrayList<Integer>> pos1 = seq.positions.get(sid);
            ArrayList<ArrayList<Integer>> pos2 = seq1.positions.get(sid);
            for(int i = 0; i < pos1.size(); i++){
                ArrayList<Integer> l1 = pos1.get(i);
                for(int j = 0; j < pos2.size(); j++){
                    ArrayList<Integer> l2 = pos2.get(j);
                    Integer p1 = l1.get(len-1);
                    Integer p2 = l2.get(0);
                    if(p1 >= p2){ // p1在p2的后面，不能反向扩展
                        continue;
                    }

                    // gap constraint
                    if(isGap) {
                        Event e1 = line.get(p1);
                        Event e2 = line.get(p2);
                        int relation = relation(e1, e2);
                        int time = e2.start - e1.end;
                        if(relation == '1' && (time < constraint.mingap || time > constraint.maxgap)){
                            continue;
                        }
                    }

                    // target constraint 
                    // seq.prefix: 记录target下一个需要匹配的位置， p2: 表示line从p2开始匹配
                    if(isTarget && NotContainTarget(constraint.target, line, seq.prefix, p2)){
                        continue;
                    }

                    // relation constraint
                    if(isRelation && seq.prefix != 0 && constraint.target.length > seq.prefix && label == constraint.target[seq.prefix]){
                        var tPositions = seq.targetPositions.get(sid).get(i);
                        if(!targetConstraint(line, tPositions, p2, constraint.relations)){
                            continue;
                        }
                    }

                    // 计算关系
                    String rela = calculateRelation(seq.relations, l1, line, p1, p2);

                    // 添加rela关系到rela2position,
                    if (!rela2position.containsKey(rela)) {
                        rela2position.put(rela, new HashMap<>());
                        rela2sids.put(rela, new BitSet());
                    }
                    if (!rela2position.get(rela).containsKey(sid)) {
                        rela2position.get(rela).put(sid, new ArrayList<>());
                        rela2sids.get(rela).set(sid);
                    }
                    ArrayList<Integer> positions = new ArrayList<>(l1);
                    positions.add(p2);
                    rela2position.get(rela).get(sid).add(positions);

                    // 添加target position
                    if(isRelation && (seq.prefix != 0 || label == constraint.target[seq.prefix])){
                        if(!rela2targetPosition.containsKey(rela)){
                            rela2targetPosition.put(rela, new HashMap<>());
                        }
                        if(!rela2targetPosition.get(rela).containsKey(sid)){
                            rela2targetPosition.get(rela).put(sid, new ArrayList<>());
                        }
                        ArrayList<Integer> targetPositions = new ArrayList<>();
                        if(seq.prefix != 0){
                            // copy the old target position
                            targetPositions = new ArrayList<>(seq.targetPositions.get(sid).get(i));
                        }
                        // add new target position
                        if(constraint.target.length > seq.prefix && label == constraint.target[seq.prefix]) {
                            targetPositions.add(p2);
                        }
                        rela2targetPosition.get(rela).get(sid).add(targetPositions);
                    }
                }
            }
            sid = lines.nextSetBit(sid+1);
        }

        // 进行剪枝, 删除支持度小于minsup的那些关系
        rela2position.entrySet().removeIf(entry -> entry.getValue().size() < minsup);

        for(String rela : rela2position.keySet()){
            // 合并label
            ArrayList<Integer> labels = new ArrayList<>(seq.labels);
            labels.add(label);

            // 输出所有包含target的频繁序列
            if(!NotContainTarget(constraint.target, labels, 0)){
                // System.out.println("labels: " + labels + ", rela: " + rela + ", sup: " + rela2position.get(rela).size());
                maxSeqLen = Math.max(maxSeqLen, labels.size());
                totalCount++;
                String buffer = "labels: " + labels + ", rela: " + rela + ", sup: " + rela2position.get(rela).size() + "\n";
                write.write(buffer);
            }

            // 计算当前序列的前缀
            int prefix = (constraint.target.length > seq.prefix && label == constraint.target[seq.prefix]) ? seq.prefix + 1 : seq.prefix;

            // 扩展序列
            Sequence next = new Sequence(labels, rela, rela2position.get(rela), rela2sids.get(rela), prefix, rela2targetPosition.get(rela));
            for(Integer nextLabel : one){
                findPattern(next, nextLabel, len+1);
            }
        }
    }

    public boolean targetConstraint(ArrayList<Event> line, ArrayList<Integer> l1, int p2, String relations){
        int len = l1.size();
        int rlen = (len*(len-1)) / 2;
        Event e2 = line.get(p2);
        for(int i = 0; i < l1.size(); i++){
            if(relation(line.get(l1.get(i)), e2) != relations.charAt(rlen+i)){
                return false;
            }
        }
        return true;
    }

    // 读取一行数据并进行处理
    public BitSet retainLine(Sequence q1, Sequence q2){
        BitSet sids = (BitSet) q1.sids.clone();
        sids.and(q2.sids);
        return sids;
    }

    public String calculateRelation(String relations, ArrayList<Integer> l1, ArrayList<Event> line, int p1, int p2) {
        StringBuilder relaNew = new StringBuilder(relations);
        int len = l1.size(); // 带扩展序列的长度
        int rlen = relations.length(); // 记录关系的长度

        if(relation(line.get(p1), line.get(p2)) == 6) {
            relaNew.append(relations, rlen - len + 1, rlen).append("6");
        } else {
            Event e = line.get(p2);
            for (Integer pos : l1) {
                relaNew.append(relation(line.get(pos), e));
            }
        }
        return relaNew.toString();
    }

    // 获得所有长度为1的频繁模式，放在one里面
    private void calculateAllL1Seq() {
        for(Map.Entry<Integer, Integer> e : label2sup.entrySet()){
            if(e.getValue() >= minsup){
                one.add(e.getKey());
            } else {
                // 删除所有不频繁的label
                label2seq.remove(e.getKey());
            }
        }
    }

    // 数据格式：行ID，标签，开始时间，结束时间
    public void readDatabase() throws IOException {
        String thisLine;
        int currentSeqId = -1; // 记录当前行的行ID
        ArrayList<Event> currentSeq = new ArrayList<>(); // 记录当前的序
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(this.input)))) {
            while((thisLine = reader.readLine()) != null){
                String[]  tokens = thisLine.split(split);
                int sid = Integer.parseInt(tokens[0]);   // 记录序列id
                int label = Integer.parseInt(tokens[1]); // 记录label
                int start = Integer.parseInt(tokens[2]); // 记录开始时间
                int end = Integer.parseInt(tokens[3]);   // 记录结束时间

                // 最开始的序列id
                if(currentSeqId == -1){
                    currentSeqId = sid;
                }

                // 如果sid不等于currentSeqId，说明当前序列已经全部读入进来了，进行解析工作
                if(sid != currentSeqId){
                    parseSequence(currentSeqId, currentSeq);
                    currentSeqId = sid;
                    currentSeq.clear();
                }

                // 添加到当前序列中
                currentSeq.add(new Event(label, start, end));
            }
            // 解析最后一个sequence
            parseSequence(currentSeqId, currentSeq);
            currentSeq.clear();
        } catch (IOException e){
           e.printStackTrace();
        }
    }

    // 解析一行数据，计算label2line, label2sup
    public void parseSequence(int sid, ArrayList<Event> sequence){
        totalTargetSeq++;
        Collections.sort(sequence);
        // 如果不包含目标序列，直接return;
        if(isTarget && NotContainTarget(this.constraint.target, sequence)){
            return;
        }
        // 记录每一行出现的label的种类，用于计算支持度
        Set<Integer> noDuplicate = new HashSet<>();
        for(int i = 0; i < sequence.size(); i++){
            int label = sequence.get(i).label;

            // 收集每个label所在的行
            int prefix = (isTarget && label == this.constraint.target[0]) ? 1 : 0;
            if (!label2seq.containsKey(label)) {
                label2seq.put(label, new Sequence(new ArrayList<>(List.of(label)), "", new HashMap<>(), new BitSet(), prefix, new HashMap<>()));
            }
            Sequence list = label2seq.get(label);
            if (!list.positions.containsKey(sid)) {
                list.positions.put(sid, new ArrayList<>());
                list.sids.set(sid);
            }
            list.positions.get(sid).add(new ArrayList<>(List.of(i)));
            
            // add target position and prefix
            if(isRelation && prefix == 1){
                if(!list.targetPositions.containsKey(sid)){
                    list.targetPositions.put(sid, new ArrayList<>());
                }
                list.targetPositions.get(sid).add(new ArrayList<>(List.of(i)));
            }

            // 计算所有label的支持度
            if(!noDuplicate.contains(label)){
                noDuplicate.add(label);
                label2sup.merge(label, 1, Integer::sum);
            }
        }
        database.put(sid, new ArrayList<>(sequence));
    }

    // 如果sequence不包含target返回true
    public boolean NotContainTarget(int[] target, ArrayList<Event> sequence){
        int len = target.length;
        int index = 0;
        for(Event event : sequence){
            if(event.label == target[index]){
                index++;
            }
            if(index >= len){
                return false;
            }
        }
        return true;
    }
    // 如果sequence不包含target返回true, ignore没有用
    public boolean NotContainTarget(int[] target, ArrayList<Integer> sequence, int ignore){
        if(!isTarget){
            return false;
        }
        int len = target.length;
        int index = 0;
        for(Integer label : sequence){
            if(label == target[index]){
                index++;
            }
            if(index >= len){
                return false;
            }
        }
        return true;

    }
    // 如果sequence不包含target返回true
    public boolean NotContainTarget(int[] target, ArrayList<Event> sequence, int p1, int p2){
        if(target.length == p1) return false;
        int len = target.length;
        int index = p1;
        for(int i = p2; i < sequence.size(); i++){
            if(sequence.get(i).label == target[index]){
                index++;
            }
            if(index >= len){
                return false;
            }
        }

        return true;
    }

    // 1: before, 2: meets, 3: overlaps, 4: contains, 5: finish-by, 6: equal, 7: stars
    public char relation(Event a, Event b){
        if(a.start == b.start && a.end == b.end){
            return '6';
        } else if(a.start == b.start && b.end > a.end){
            return '7';
        } else if(a.end == b.end && b.start > a.start){
            return '5';
        } else if(b.start > a.start && a.end > b.end){
            return '4';
        } else if(a.end > b.start && b.start > a.start){
            return '3';
        } else if(b.start == a.end){
            return '2';
        } else {
            return '1';
        }
    }

    // 打印挖掘信息
    public void printStatistics() {
        System.out.println("================= IPMFC - STATS =======================");
        System.out.println(" Filename: " + input);
        System.out.println(" Constraints: " + constraint);
        System.out.println(" Total target sequence: " + this.totalTargetSeq);
        System.out.println(" Threshold: " + this.minsup);
        System.out.println(" Percent: " + this.percent);
        System.out.println(" Total frequency target pattern: " + this.totalCount);
        System.out.println(" Total Time ~ " + (endTimestamp - startTimestamp)/1000 + " s");
        System.out.println(" Max Sequence Len: " + maxSeqLen);
        System.out.println(" 1-Sequence num: " + SeqLen1Count);
        System.out.println("========================================================"+" \n");
    }
}