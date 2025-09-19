package queue;

public class QueueKeys {

    public static String queueKey(long ticketItemId) {
        return "queue:ticket_item:" + ticketItemId;
    }

    public static String passKey(long ticketItemId, long userId) {
        return "pass:ticket_item:" + ticketItemId + ":user:" + userId;
    }

    public static String configKey(long ticketItemId) {
        return "config:ticket_item:" + ticketItemId;
    }
}

