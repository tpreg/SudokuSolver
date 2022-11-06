package hu.unideb.inf.sudokusolver;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.parseInt;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

final class Solver {
	public static Integer[] search(final ColumnNode h, final List<Node> solution) {
		if (h.getRight().equals(h)) {
			return printSolution(solution);
		}
		var c = chooseNextColumn(h);
		cover(c);
		var r = c.getDown();
		while (!Objects.equals(r, c)) {
			solution.add(r);
			var j = r.getRight();
			while (!Objects.equals(j, r)) {
				cover(j.getColumn());
				j = j.getRight();
			}
			final var digits = search(h, solution);
			if (digits.length != 0) {
				return digits;
			}
			solution.remove(r);
			c = r.getColumn(); // why?
			var i = r.getLeft();
			while (!Objects.equals(i, r)) {
				uncover(i.getColumn());
				i = i.getLeft();
			}
			r = r.getDown();
		}
		uncover(c);
		return new Integer[0];
	}

	private static Integer[] printSolution(final List<Node> solution) {
		final var lists = new ArrayList<List<String>>();
		for (final var node : solution) {
			final var nodes = new ArrayList<String>();
			nodes.add(node.getColumn().getName());
			for (var right = node.getRight(); !Objects.equals(right, node); right = right.getRight()) {
				nodes.add(right.getColumn().getName());
			}
			nodes.sort(naturalOrder());
			lists.add(nodes);
		}
		return lists.stream()
				.sorted(comparing(o -> o.get(2)))
				.map(strings -> parseInt(String.valueOf(strings.get(0).charAt(2))))
				.toArray(Integer[]::new);
	}

	private static void uncover(final Node column) {
		for (var i = column.getUp(); !Objects.equals(i, column); i = i.getUp()) {
			for (var j = i.getLeft(); !Objects.equals(j, i); j = j.getLeft()) {
				j.getColumn().incrementSize();
				j.getDown().setUp(j);
				j.getUp().setDown(j);
			}
		}
		column.getRight().setLeft(column);
		column.getLeft().setRight(column);
	}

	private static void cover(final Node column) {
		column.getRight().setLeft(column.getLeft());
		column.getLeft().setRight(column.getRight());

		for (var i = column.getDown(); !Objects.equals(i, column); i = i.getDown()) {
			for (var j = i.getRight(); !Objects.equals(j, i); j = j.getRight()) {
				j.getDown().setUp(j.getUp());
				j.getUp().setDown((j.getDown()));
				j.getColumn().decrementSize();
			}
		}
	}

	private static ColumnNode chooseNextColumn(final ColumnNode h) {
		Integer minSize = MAX_VALUE;
		var minColumn = (ColumnNode) h.getRight();
		var current = minColumn;
		while (!Objects.equals(current, h)) {
			if (current.getSize() < minSize) {
				minColumn = current;
				minSize = current.getSize();
			}
			current = (ColumnNode) current.getRight();
		}
		return minColumn;
	}

	static ColumnNode init(final Integer[] sudokuPuzzle) {
		final var rootHeader = ColumnNode.createRootHeader();
		final var headersMap = SparseMatrix.createHeadersMap(rootHeader);

		final var matrix = SparseMatrix.createSparseMatrixFromSudokuPuzzle(sudokuPuzzle, headersMap);
		final var givenNumbers = matrix.stream()
				.filter(nodeList -> nodeList.stream().anyMatch(Node::isGiven))
				.flatMap(List::stream)
				.map(Node::getColumnName)
				.collect(toList());
		matrix.removeIf(nodes -> nodes.stream().anyMatch(node -> givenNumbers.contains(node.getColumnName()) && !node.isGiven()));
		final var nodes = matrix.stream()
				.flatMap(List::stream)
				.collect(groupingBy(Node::getColumnName));

		matrix.forEach(Solver::setHeadersLeft);
		matrix.forEach(Solver::setHeadersRight);
		final var headers = List.copyOf(headersMap.values());
		setHeadersLeft(headers);
		setHeadersRight(headers);
		setHeadersUp(headers, nodes);
		setHeadersDown(headers, nodes);
		setHeadersSize(headers, nodes);
		headers.forEach(ColumnNode::setColumn);
		return rootHeader;
	}

	private static void setHeadersLeft(final List<? extends Node> headers) {
		final var count = headers.size() - 1;
		for (var i = count; i >= 0; i--) {
			final var current = headers.get(i);
			if (i - 1 >= 0) {
				current.setLeft(headers.get(i - 1));
			} else {
				current.setLeft(headers.get(count));
			}
		}
	}

	private static void setHeadersRight(final List<? extends Node> headers) {
		final var count = headers.size();
		for (var i = 0; i < count; i++) {
			final var current = headers.get(i);
			if (i + 1 < count) {
				current.setRight(headers.get(i + 1));
			} else {
				current.setRight(headers.get(0));
			}
		}
	}

	private static void setHeadersUp(final List<ColumnNode> headers, final Map<String, List<Node>> nodes) {
		for (final var header : headers) {
			final var headerName = header.getName();
			if (nodes.containsKey(headerName)) {
				final var list = Stream.concat(Stream.of(header), nodes.get(headerName).stream()).collect(toList());
				final var count = list.size() - 1;
				for (var i = count; i >= 0; i--) {
					final var current = list.get(i);
					if (i - 1 >= 0) {
						current.setUp(list.get(i - 1));
					} else {
						current.setUp(list.get(count));
					}
				}
			}
		}
	}

	private static void setHeadersDown(final List<ColumnNode> headers, final Map<String, List<Node>> nodes) {
		for (final var header : headers) {
			final var headerName = header.getName();
			if (nodes.containsKey(headerName)) {
				final var list = Stream.concat(Stream.of(header), nodes.get(headerName).stream()).collect(toList());
				final var count = list.size();
				for (var i = 0; i < count; i++) {
					final var current = list.get(i);
					if (i + 1 < count) {
						current.setDown(list.get(i + 1));
					} else {
						current.setDown(list.get(0));
					}
				}
			}
		}
	}

	private static void setHeadersSize(final List<ColumnNode> headers, final Map<String, List<Node>> nodes) {
		headers.stream() //
				.filter(header -> nodes.containsKey(header.getName())) //
				.forEach(header -> header.setSize(nodes.get(header.getName()).size()));
	}

}
