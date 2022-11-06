package hu.unideb.inf.sudokusolver;

public class Node {
    private Node up;
    private Node down;
    private Node left;
    private Node right;
    private ColumnNode column;
    private boolean given;

    Node() {
    }

    public Node(final ColumnNode columnNode, final boolean given) {
        this.column = columnNode;
        this.given = given;
    }

    public String getColumnName() {
        return this.column.getName();
    }

    public Node getUp() {
        return this.up;
    }

    public void setUp(final Node up) {
        this.up = up;
    }

    public Node getDown() {
        return this.down;
    }

    public void setDown(final Node down) {
        this.down = down;
    }

    public Node getLeft() {
        return this.left;
    }

    public void setLeft(final Node left) {
        this.left = left;
    }

    public Node getRight() {
        return this.right;
    }

    public void setRight(final Node right) {
        this.right = right;
    }

    public ColumnNode getColumn() {
        return this.column;
    }

    public void setColumn(final ColumnNode column) {
        this.column = column;
    }

    public boolean isGiven() {
        return this.given;
    }

}
