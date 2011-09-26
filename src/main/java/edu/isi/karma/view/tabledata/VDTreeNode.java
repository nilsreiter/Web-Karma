/**
 * 
 */
package edu.isi.karma.view.tabledata;

import java.util.LinkedList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONWriter;

import edu.isi.karma.rep.HNode;
import edu.isi.karma.rep.Node;
import edu.isi.karma.view.Margin;
import edu.isi.karma.view.VWorkspace;
import edu.isi.karma.view.tableheadings.VHTreeNode;

/**
 * @author szekely
 * 
 */
public class VDTreeNode {

	private final Node node;

	private final VHTreeNode vhTreeNode;

	private final VDRow containerVDRow;

	private final List<VDRow> nestedTableRows = new LinkedList<VDRow>();

	/**
	 * The depth of this tree node. The root table has depth 0.
	 */
	private int depth = 0;

	/**
	 * If there is a nested table, record the style of margin that will be shown
	 * above and below.
	 */
	private Margin margin = null;

	/**
	 * Number of levels of data needed to show this node.
	 */
	private int numLevels = -1;

	public VDTreeNode(Node node, VHTreeNode vhTreeNode, VDRow containerVDRow) {
		super();
		this.node = node;
		this.vhTreeNode = vhTreeNode;
		this.containerVDRow = containerVDRow;

	}

	public List<VDRow> getNestedTableRows() {
		return nestedTableRows;
	}

	public boolean hasNestedTable() {
		return !nestedTableRows.isEmpty();
	}

	int getDepth() {
		return depth;
	}

	void setDepth(int depth) {
		this.depth = depth;
	}

	Node getNode() {
		return node;
	}

	Margin getMargin() {
		return margin;
	}

	int getNumLevels() {
		return numLevels;
	}

	HNode getHNode(VWorkspace vWorkspace) {
		return vWorkspace.getRepFactory().getHNode(node.gethNodeId());
	}

	/**
	 * @param vWorkspace
	 * @return if this is a leaf node, then the HTableId of the containing
	 *         HTable. If this is a nested table, then it is the HTableId of the
	 *         parent table of the nested table.
	 */
	String getContainerHTableId(VWorkspace vWorkspace) {
		return getHNode(vWorkspace).getHTableId();
	}

	void firstPassTopDown(VWorkspace vWorkspace) {
		depth = containerVDRow.getDepth();

		if (hasNestedTable()) {
			margin = new Margin(getContainerHTableId(vWorkspace), depth);
		}

		// Now go top down.
		for (VDRow r : nestedTableRows) {
			r.setFillHTableId(getHNode(vWorkspace).getNestedTable().getId());
			r.firstPassTopDown(vWorkspace);
		}
	}

	void secondPassBottomUp(VWorkspace vWorkspace) {
		// First recurse.
		for (VDRow r : nestedTableRows) {
			r.secondPassBottomUp(vWorkspace);
		}

		if (containerVDRow != null) {
			// Propagate the margin up.
			containerVDRow.accumulateMargin(margin);
		}

		// Calculate numLevels.
		if (hasNestedTable()) {
			int totalLevels = 0;
			for (VDRow r : nestedTableRows) {
				totalLevels += r.getNumLevels();
			}
			numLevels = totalLevels;
		} else {
			numLevels = 1;
		}
	}

	/*****************************************************************
	 * 
	 * Debugging Support
	 * 
	 *****************************************************************/

	void prettyPrintJson(JSONWriter jw, boolean verbose) throws JSONException {
		jw.object()//
				.key("_node").value(node.toString())//
				.key("_depth").value(depth)//
				.key("_margin").value(Margin.getMarginsString(margin))//
				.key("_numLevels").value(numLevels)//
		;
		if (verbose) {
			jw.key("hTreeNode");
			vhTreeNode.prettyPrintJson(jw, false, false);
		}
		if (!nestedTableRows.isEmpty()) {
			jw.key("rows").array();
			for (VDRow r : nestedTableRows) {
				r.prettyPrintJson(jw, verbose);
			}
			jw.endArray();
		}

		jw.endObject();
	}

}
