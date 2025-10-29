package nl.han.ica.icss.ast;

import nl.han.ica.icss.checker.SemanticError;
import java.util.ArrayList;
import java.util.List;

public class ASTNode {

    private SemanticError error = null;
    protected ASTNode parent = null;

    protected ArrayList<ASTNode> children = new ArrayList<>();

    public ASTNode getParent() {
        return parent;
    }

    public void setParent(ASTNode parent) {
        this.parent = parent;
    }

    public ArrayList<ASTNode> getChildren() {
        return children;
    }

    public ASTNode addChild(ASTNode child) {
        child.setParent(this);
        children.add(child);
        return this;
    }

    public ASTNode removeChild(ASTNode child) {
        children.remove(child);
        child.setParent(null);
        return this;
    }

    public String getNodeLabel() {
        return "ASTNode";
    }

    public SemanticError getError() {
        return this.error;
    }

    public void setError(String description) {
        this.error = new SemanticError(description);
    }

    public boolean hasError() {
        return error != null;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        toString(result);
        return result.toString();
    }

    private void toString(StringBuilder builder) {
        builder.append("[");
        builder.append(getNodeLabel());
        builder.append("|");
        for (ASTNode child : getChildren()) {
            child.toString(builder);
        }
        builder.append("]");
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ASTNode))
            return false;
        List<ASTNode> thisChildren = this.getChildren();
        List<ASTNode> otherChildren = ((ASTNode) o).getChildren();
        if (otherChildren.size() != thisChildren.size())
            return false;
        for (int i = 0; i < thisChildren.size(); i++) {
            if (!thisChildren.get(i).equals(otherChildren.get(i))) {
                return false;
            }
        }
        return true;
    }
}
