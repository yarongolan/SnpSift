package ca.mcgill.mcb.pcingola.snpSift.lang.expression;

import ca.mcgill.mcb.pcingola.snpEffect.LossOfFunction;
import ca.mcgill.mcb.pcingola.vcf.VcfNmd;

/**
 * A NMD field form SnpEff:
 *
 * E.g.:  'NMD[2].GENE'
 *
 * @author pablocingolani
 */
public class FieldNmd extends FieldLof {

	public FieldNmd(String name, Expression indexExpr) {
		super(name, indexExpr); // Add an 'NMD.' at the beginning
	}

	@Override
	protected void init() {
		infoFieldName = LossOfFunction.VCF_INFO_NMD_NAME;

		if (name != null) {
			String headerName = infoFieldName + "." + name;
			fieldNum = VcfNmd.fieldNum(headerName);
			if (fieldNum < 0) { throw new RuntimeException("No such " + infoFieldName + " subfield '" + headerName + "'"); }
		}
	}
}
