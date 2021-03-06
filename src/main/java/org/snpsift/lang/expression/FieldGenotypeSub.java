package org.snpsift.lang.expression;

import org.snpeff.vcf.VcfEntry;
import org.snpeff.vcf.VcfGenotype;
import org.snpsift.lang.expression.FieldIterator.IteratorType;

/**
 * A field:
 * E.g.:  'GEN[2].PL[3]'
 *
 * @author pablocingolani
 */
public class FieldGenotypeSub extends FieldGenotype {

	Expression indexExpr2;

	public FieldGenotypeSub(String name, Expression indexExpr, Expression indexExpr2) {
		super(name, indexExpr);
		this.indexExpr2 = indexExpr2;
	}

	/**
	 * Get a field from VcfEntry
	 */
	@Override
	public String getFieldString(VcfEntry vcfEntry) {
		VcfGenotype vcfGenotype = evalGenotype(vcfEntry);
		String value = vcfGenotype.get(name);
		if (value == null) return (String) fieldNotFound(vcfEntry);

		String sub[] = value.split(",");

		// Find second index value
		int index = evalIndex(vcfGenotype, indexExpr2);

		// Is this field 'iterable'?
		int idx = index;
		if (index < 0) {
			FieldIterator.get().setMax(IteratorType.GENOTYPE_VAR, sub.length - 1);
			FieldIterator.get().setType(index);
			idx = FieldIterator.get().get(IteratorType.GENOTYPE_VAR);
		}

		if (sub.length <= idx) return "";
		return sub[idx];
	}

	/**
	 * Get a field from VcfGenotype
	 */
	@Override
	public String getFieldString(VcfGenotype vcfGenotype) {
		String value = super.getFieldString(vcfGenotype);
		if (value == null) return (String) gtFieldNotFound(vcfGenotype);
		String sub[] = value.split(",");

		// Find second index value
		int index = evalIndex(vcfGenotype, indexExpr2);

		// Is this field 'iterable'?
		int idx = index;
		if (index < 0) {
			FieldIterator.get().setMax(IteratorType.GENOTYPE_VAR, sub.length - 1);
			FieldIterator.get().setType(index);
			idx = FieldIterator.get().get(IteratorType.GENOTYPE_VAR);
		}

		if (sub.length <= idx) return "";
		return sub[idx];
	}

	@Override
	protected boolean isSub() {
		return true;
	}

	@Override
	public String toString() {
		return "GEN[" + indexExpr + "]." + name + "[" + indexExpr2 + "]";
	}
}
