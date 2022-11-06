package hu.unideb.inf.sudokusolver;

public final class ColumnNode extends Node {
	private final String name;
	private Integer size;

	public ColumnNode(final String name) {
		super();
		this.name = name;
	}

	public static ColumnNode createRootHeader() {
		return new ColumnNode("h");
	}

	public Integer getSize() {
		return this.size;
	}

	public void setSize(final Integer size) {
		this.size = size;
	}

	public String getName() {
		return this.name;
	}

	public void incrementSize() {
		this.size++;
	}

	public void decrementSize() {
		this.size--;
	}

	public void setColumn() {
		super.setColumn(this);
	}

}
