package org.jc.todo;

public class TodoItem {
    private String id;
    private String text;
    private String status;

    public TodoItem() {
    }

    public TodoItem(String id, String text, String status) {
        this.id = id;
        this.text = text;
        this.status = status;
    }

    // getter & setter
    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getStatus() {
        return status;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
