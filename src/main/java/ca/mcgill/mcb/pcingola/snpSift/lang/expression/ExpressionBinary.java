package ca.mcgill.mcb.pcingola.snpSift.lang.expression;

import ca.mcgill.mcb.pcingola.snpSift.lang.Value;
import ca.mcgill.mcb.pcingola.vcf.VcfEntry;
import ca.mcgill.mcb.pcingola.vcf.VcfGenotype;

/**
 * Binary condition
 *
 * @author pcingola
 */
public abstract class ExpressionBinary extends Expression {

	protected Expression left;
	protected Expression right;

	public ExpressionBinary(Expression left, Expression right, String operator) {
		super(operator);
		this.right = right;
		this.left = left;
	}

	@Override
	public Value eval(VcfEntry vcfEntry) {
		Value lval = left.eval(vcfEntry);
		Value rval = right != null ? right.eval(vcfEntry) : null;
		return evalOp(lval, rval);
	}

	@Override
	public Value eval(VcfGenotype gt) {
		Value lval = left.eval(gt);
		Value rval = right != null ? right.eval(gt) : null;
		return evalOp(lval, rval);
	}

	protected abstract Value evalOp(Value lval, Value rval);

	@Override
	public String toString() {
		return "( " + left + " " + operator + " " + right + " )";
	}

}
