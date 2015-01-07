package ca.mcgill.mcb.pcingola.snpSift.lang.expression;

import java.util.List;

import ca.mcgill.mcb.pcingola.snpSift.lang.expression.FieldIterator.IteratorType;
import ca.mcgill.mcb.pcingola.vcf.EffFormatVersion;
import ca.mcgill.mcb.pcingola.vcf.VcfEffect;
import ca.mcgill.mcb.pcingola.vcf.VcfEntry;
import ca.mcgill.mcb.pcingola.vcf.VcfHeader;
import ca.mcgill.mcb.pcingola.vcf.VcfHeaderInfo;
import ca.mcgill.mcb.pcingola.vcf.VcfInfoType;

/**
 * An 'EFF' field form SnpEff:
 *
 * E.g.:  'EFF[2].GENE'
 *
 * @author pablocingolani
 */
public class FieldEff extends FieldSub {

	int fieldNum = -1;
	EffFormatVersion formatVersion = null;

	/**
	 * Constructor
	 * @param formatVersion : Can be null (it will be guessed)
	 */
	public FieldEff(String name, Expression idxExpr, EffFormatVersion formatVersion) {
		super(name, idxExpr); // Add an 'ANN.' or 'EFF.' at the beginning
		this.formatVersion = formatVersion;
	}

	/**
	 * Should this be 'EFF' or 'ANN'?
	 */
	String annEff() {
		return (formatVersion == null || formatVersion.isAnn() ? "ANN" : "EFF");
	}

	/**
	 * Get field number by name
	 */
	int fieldNum(VcfEffect eff) {
		String headerName = annEff() + "." + name;

		if (formatVersion == null) formatVersion = eff.formatVersion();
		int fieldNum = VcfEffect.fieldNum(headerName, formatVersion);

		if (fieldNum < 0) throw new RuntimeException("No such subfield '" + headerName + "'");
		return fieldNum;
	}

	/**
	 * Get a field from VcfEntry
	 */
	@Override
	public String getFieldString(VcfEntry vcfEntry) {
		// Get all effects
		List<VcfEffect> effects = vcfEntry.parseEffects(formatVersion);
		if (effects.size() <= 0) return null;

		// Find index value
		int index = evalIndex(vcfEntry);

		// Find field
		if (index >= effects.size()) return null;

		// Is this field 'iterable'?
		int idx = index;
		if (index < 0) {
			FieldIterator.get().setMax(IteratorType.EFFECT, effects.size() - 1);
			FieldIterator.get().setType(index);
			idx = FieldIterator.get().get(IteratorType.EFFECT);
		}

		// Find sub-field
		VcfEffect eff = effects.get(idx);
		String value = getSubField(eff, vcfEntry);
		return value;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public VcfInfoType getReturnType(VcfEntry vcfEntry) {
		if (name == null) return VcfInfoType.String;
		if (returnType != VcfInfoType.UNKNOWN) return returnType;

		VcfHeader vcfHeader = vcfEntry.getVcfFileIterator().getVcfHeader();

		// Is there a field 'name'
		String headerName = annEff() + "." + name;
		VcfHeaderInfo vcfInfo = vcfHeader.getVcfInfo(headerName);
		if (vcfInfo != null) returnType = vcfInfo.getVcfInfoType();
		else throw new RuntimeException("Sub-field '" + headerName + "' not found in VCF header");

		return returnType;
	}

	/**
	 * Find sub-field
	 */
	String getSubField(VcfEffect eff, VcfEntry vcfEntry) {
		if (eff == null) return (String) fieldNotFound(vcfEntry);

		// No sub-field? => Use the whole field
		if (name == null) return eff.getVcfFieldString();

		// Field number not set? Try to guess it
		if (fieldNum < 0) fieldNum = fieldNum(eff);

		// Find sub-field
		String value = eff.getVcfFieldString(fieldNum);
		if (value == null) return (String) fieldNotFound(vcfEntry);
		return value;
	}

	@Override
	public String toString() {
		return annEff() + "[" + indexExpr + "]" + (name != null ? "." + name : "");
	}
}
