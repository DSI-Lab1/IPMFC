// 记录一个事件
public class Event implements Comparable<Event> {
    int label;
    int start;
    int end;
    public Event(int label, int start, int end){
        this.label = label;
        this.start = start;
        this.end = end;
    }
    @Override
    public int compareTo(Event e){
        if(this.start != e.start){
            return this.start - e.start;
        } else if (this.end != e.end){
            return this.end - e.end;
        } else {
            return this.label - e.label;
        }
    }

    @Override
    public String toString() {
        return "Event{" + "label=" + label + ", start=" + start + ", end=" + end + '}';
    }
}
