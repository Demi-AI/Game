package shared;
import java.io.Serializable;

public class Message implements Serializable {
    public enum Type {
        MOVE, RESET, START, CHAT, SYSTEM, ASSIGN, EXIT
    }

    private Type type;
    private Object payload;

    public Message(Type type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public Type getType() {
        return type;
    }

    public Object getPayload() {
        return payload;
    }
}
