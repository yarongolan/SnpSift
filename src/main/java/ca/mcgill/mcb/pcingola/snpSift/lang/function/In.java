package ca.mcgill.mcb.pcingola.snpSift.lang.function;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import ca.mcgill.mcb.pcingola.snpSift.lang.Value;
import ca.mcgill.mcb.pcingola.snpSift.lang.expression.Expression;
import ca.mcgill.mcb.pcingola.vcf.VcfEntry;
import ca.mcgill.mcb.pcingola.vcf.VcfGenotype;

/**
 * Is an expression in a set?
 *
 * @author pablocingolani
 */
public class In extends Function {

	ArrayList<HashSet<String>> sets;
	Expression expression;
	Expression setIdxExpr;

	public In(ArrayList<HashSet<String>> sets, Expression expression, Expression setIdxExpr) {
		super("in");
		this.sets = sets;
		this.expression = expression;
		this.setIdxExpr = setIdxExpr;
	}

	@Override
	public Value eval(VcfEntry vcfEntry) {
		// Evaluate expression
		String val = expression.eval(vcfEntry).asString();

		// Get set
		Value idx = setIdxExpr.eval(vcfEntry);
		Set<String> set = sets.get((int) idx.asInt());

		// Is 'expression' in set?
		return set.contains(val) ? Value.TRUE : Value.FALSE;
	}

	@Override
	public Value eval(VcfGenotype gt) {
		String val = expression.eval(gt).asString();

		// Get set
		Value idx = setIdxExpr.eval(gt);
		Set<String> set = sets.get((int) idx.asInt());

		// Is 'expression' in set?
		return set.contains(val) ? Value.TRUE : Value.FALSE;
	}

	@Override
	public String toString() {
		return expression + " " + operator + " SET[" + setIdxExpr + "]";
	}

}
