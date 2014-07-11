package ca.mcgill.mcb.pcingola.snpSift;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.mcgill.mcb.pcingola.fileIterator.DbNsfpEntry;
import ca.mcgill.mcb.pcingola.fileIterator.DbNsfpFileIterator;
import ca.mcgill.mcb.pcingola.fileIterator.VcfFileIterator;
import ca.mcgill.mcb.pcingola.util.Gpr;
import ca.mcgill.mcb.pcingola.util.Timer;
import ca.mcgill.mcb.pcingola.vcf.VcfEntry;
import ca.mcgill.mcb.pcingola.vcf.VcfInfoType;

/**
 * Annotate a VCF file with dbNSFP.
 *
 * The dbNSFP is an integrated database of functional predictions from multiple algorithms for the comprehensive
 * collection of human non-synonymous SNPs (NSs). Its current version (ver 1.1) is based on CCDS version 20090327
 * and includes a total of 75,931,005 NSs. It compiles prediction scores from four prediction algorithms (SIFT,
 * Polyphen2, LRT and MutationTaster), two conservation scores (PhyloP and GERP++) and other related information.
 *
 * References:
 *
 * 		http://sites.google.com/site/jpopgen/dbNSFP
 *
 * 		Paper: Liu X, Jian X, and Boerwinkle E. 2011. dbNSFP: a lightweight database of human non-synonymous SNPs and their
 * 		functional predictions. Human Mutation. 32:894-899.
 *
 * @author lletourn
 *
 */
public class SnpSiftCmdDbNsfp extends SnpSift {

	public static final String VCF_INFO_PREFIX = "dbNSFP_";
	public static final String DEFAULT_FIELDS_NAMES_TO_ADD = "" // Default fields to add
			+ "Uniprot_acc," //
			+ "Interpro_domain," // Domain
			+ "SIFT_pred," // SIFT predictions
			+ "Polyphen2_HDIV_pred,Polyphen2_HVAR_pred," // Polyphen predictions
			+ "LRT_pred," // LRT predictions
			+ "MutationTaster_pred," // MutationTaser predictions
			+ "GERP++_NR,GERP++_RS," // GERP
			+ "phastCons100way_vertebrate," // Conservation
			+ "1000Gp1_AF,1000Gp1_AFR_AF,1000Gp1_EUR_AF,1000Gp1_AMR_AF,1000Gp1_ASN_AF," // Allele frequencies 1000 Genomes project
			+ "ESP6500_AA_AF,ESP6500_EA_AF" // Allele frequencies Exome sequencing project
	;

	public static final int MIN_JUMP = 100;
	public static final int SHOW_ANNOTATED = 1;

	protected Map<String, String> fieldsToAdd;
	protected Map<String, String> fieldsDescription;
	protected Map<String, String> fieldsType;
	protected boolean annotateEmpty; // Annotate empty fields as well?
	protected boolean collapseRepeatedValues; // Collapse values if repeated?
	protected boolean tabixCheck = true;
	protected String vcfFileName;
	protected int count = 0;
	protected int countAnnotated = 0;
	protected DbNsfpFileIterator dbNsfpFile;
	protected VcfFileIterator vcfFile;
	protected DbNsfpEntry currentDbEntry;
	protected String fieldsNamesToAdd;
	String latestChromo = "";

	public SnpSiftCmdDbNsfp(String args[]) {
		super(args, "dbnsfp");
	}

	/**
	 * Add some lines to header before showing it
	 *
	 * @param vcfFile
	 */
	@Override
	protected void addHeader(VcfFileIterator vcfFile) {
		super.addHeader(vcfFile);
		for (String fieldName : fieldsToAdd.keySet()) {
			// Get type
			String type = fieldsType.get(fieldName);
			if (type == null) {
				System.err.println("WARNING: Cannot find type for field '" + fieldName + "', using 'String'.");
				type = VcfInfoType.String.toString();
			}

			vcfFile.getVcfHeader().addLine("##INFO=<ID=" + VCF_INFO_PREFIX + fieldName + ",Number=A,Type=" + type + ",Description=\"" + fieldsToAdd.get(fieldName) + "\">");
		}
	}

	ArrayList<VcfEntry> annotate(boolean createList) {
		ArrayList<VcfEntry> list = (createList ? new ArrayList<VcfEntry>() : null);

		// Initialize annotations
		try {
			initAnnotate();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		// Annotate VCF file
		boolean showHeader = true;
		int pos = -1;
		String chr = "";
		for (VcfEntry vcfEntry : vcfFile) {
			try {
				// Show header?
				if (showHeader) {
					// Add VCF header
					addHeader(vcfFile);
					String headerStr = vcfFile.getVcfHeader().toString();
					if (!headerStr.isEmpty()) System.out.println(headerStr);
					showHeader = false;

					// Check that the fields we want to add are actually in the database
					checkFieldsToAdd();
				}

				// Check if file is sorted
				if (vcfEntry.getChromosomeName().equals(chr) && vcfEntry.getStart() < pos) {
					fatalError("Your VCF file should be sorted!" //
							+ "\n\tPrevious entry " + chr + ":" + pos//
							+ "\n\tCurrent entry  " + vcfEntry.getChromosomeName() + ":" + (vcfEntry.getStart() + 1)//
					);
				}

				// Annotate
				annotate(vcfEntry);

				// Show
				System.out.println(vcfEntry);
				if (list != null) list.add(vcfEntry);
				count++;

				// Update chr:pos
				chr = vcfEntry.getChromosomeName();
				pos = vcfEntry.getStart();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		endAnnotate();

		// Show some stats
		if (verbose) {
			double perc = (100.0 * countAnnotated) / count;
			Timer.showStdErr("Done." //
					+ "\n\tTotal annotated entries : " + countAnnotated //
					+ "\n\tTotal entries           : " + count //
					+ "\n\tPercent                 : " + String.format("%.2f%%", perc) //
			);
		}

		return list;
	}

	/**
	 * Annotate a vcf entry
	 */
	public void annotate(VcfEntry vcf) throws IOException {
		// Find in database
		DbNsfpEntry dbEntry = findDbEntry(vcf);
		if (dbEntry == null) return;

		// Add all INFO fields that refer to this allele
		boolean annotated = false;
		StringBuilder info = new StringBuilder();
		for (String fieldKey : fieldsToAdd.keySet()) {
			info.setLength(0);

			// Find annotations for each ALT in our VcfEntry
			for (String alt : vcf.getAlts()) {
				// Are there any values to annotate?
				String val = dbEntry.getCsv(alt, fieldKey);

				// Missing or empty?
				if (annotateEmpty) {
					if (val == null) val = ".";
				} else if (isDbNsfpValueEmpty(val)) {
					val = null;
				}

				// Add value to "info"
				if (val != null) {
					if (info.length() > 0) info.append(',');
					info.append(val);
				}
			}

			// Add annotations
			if (annotateEmpty || info.length() > 0) {
				String infoStr = info.toString();
				if (infoStr.isEmpty()) infoStr = ".";
				infoStr = infoStr.replace(';', ',').replace('\t', '_').replace(' ', '_'); // Make sure all characters are valid for VCF field

				vcf.addInfo(VCF_INFO_PREFIX + fieldKey, infoStr);
				annotated = true;
			}
		}

		// Show progress
		if (annotated) {
			countAnnotated++;
			if (debug) Gpr.debug("Annotated: " + vcf.getChromosomeName() + ":" + vcf.getStart());
			else if (verbose) {
				if (countAnnotated % SHOW_ANNOTATED == 0) {
					if (countAnnotated % (100 * SHOW_ANNOTATED) == 0) System.err.print(".\n" + countAnnotated + "\t" + vcf.getChromosomeName() + ":" + vcf.getStart() + "\t");
					else System.err.print('.');
				}
			}
		}
	}

	/**
	 * Check that all fields to add are available
	 * @throws IOException
	 */
	public void checkFieldsToAdd() throws IOException {
		// Check that all fields have a descriptor (used in VCF header)
		if (verbose) {
			for (String filedName : dbNsfpFile.getFieldNames())
				if (fieldsDescription.get(filedName) == null) System.err.println("WARNING: Field (column) '" + filedName + "' does not have an approriate field descriptor.");
		}

		// Check that all "field to add" are in the database
		for (String fieldKey : fieldsToAdd.keySet())
			if (!dbNsfpFile.hasField(fieldKey)) fatalError("dbNsfp does not have field '" + fieldKey + "' (file '" + dbFileName + "')");
	}

	/**
	 * Finish up annotation process
	 */
	public void endAnnotate() {
		vcfFile.close();
		dbNsfpFile.close();
	}

	/**
	 * Find a matching db entry for a vcf entry
	 */
	public DbNsfpEntry findDbEntry(VcfEntry vcfEntry) throws IOException {
		//---
		// Find db entry
		//---
		if (debug) System.err.println("Looking for " + vcfEntry.getChromosomeName() + ":" + vcfEntry.getStart() + ". Current DB: " + (currentDbEntry == null ? "null" : currentDbEntry.getChromosomeName() + ":" + currentDbEntry.getStart()));
		while (true) {

			if (currentDbEntry == null) {
				// Null entry, try getting next entry
				currentDbEntry = dbNsfpFile.next(); // Read next DB entry

				// Still null? May be we run out of DB entries for this chromosome
				if (currentDbEntry == null) {
					// Is vcfEntry still in 'latestChromo'? Then we have no DbEntry, return null
					if (latestChromo.equals(vcfEntry.getChromosomeName())) return null; // End of 'latestChromo' section in database?

					// VCfEntry is in another chromosome? Jump to 'new' chromosome
					if (debug) Gpr.debug("New chromosome '" + latestChromo + "' != '" + vcfEntry.getChromosomeName() + "': We should jump");
					dbNsfpFile.seek(vcfEntry.getChromosomeName(), vcfEntry.getStart());
					currentDbEntry = dbNsfpFile.next();

					// Still null? well it looks like we don't have any dbEntry for this chromosome
					if (currentDbEntry == null) {
						latestChromo = vcfEntry.getChromosomeName(); // Make sure we don't try jumping again
						return null;
					}
				}
			}

			if (debug) Gpr.debug("Current Db Entry:" + currentDbEntry.getChromosomeName() + ":" + currentDbEntry.getStart() + "\tLooking for: " + vcfEntry.getChromosomeName() + ":" + vcfEntry.getStart());

			// Find entry
			if (currentDbEntry.getChromosomeName().equals(vcfEntry.getChromosomeName())) {
				// Same chromosome

				// Same position? => Found
				if (vcfEntry.getStart() == currentDbEntry.getStart()) {
					// Found db entry! Break loop and proceed with annotations
					if (debug) Gpr.debug("Found Db Entry:" + currentDbEntry.getChromosomeName() + ":" + currentDbEntry.getStart());
					return currentDbEntry;
				} else if (vcfEntry.getStart() < currentDbEntry.getStart()) {
					// Same chromosome, but positioned after => No db entry found
					if (debug) Gpr.debug("No db entry found:\t" + vcfEntry.getChromosomeName() + ":" + vcfEntry.getStart());
					return null;
				} else if ((vcfEntry.getStart() - currentDbEntry.getStart()) > MIN_JUMP) {
					// Is it far enough? Don't iterate, jump
					if (debug) Gpr.debug("Position jump:\t" + currentDbEntry.getChromosomeName() + ":" + currentDbEntry.getStart() + "\t->\t" + vcfEntry.getChromosomeName() + ":" + vcfEntry.getStart());
					dbNsfpFile.seek(vcfEntry.getChromosomeName(), vcfEntry.getStart());
					currentDbEntry = dbNsfpFile.next();
				} else {
					// Just read next entry to get closer
					currentDbEntry = dbNsfpFile.next();
				}
			} else if (!currentDbEntry.getChromosomeName().equals(vcfEntry.getChromosomeName())) {
				// Different chromosome? => Jump to chromosome
				if (debug) Gpr.debug("Chromosome jump:\t" + currentDbEntry.getChromosomeName() + ":" + currentDbEntry.getStart() + "\t->\t" + vcfEntry.getChromosomeName() + ":" + vcfEntry.getStart());

				// Jump to new position. If chromosome not found, return null
				if (!dbNsfpFile.seek(vcfEntry.getChromosomeName(), vcfEntry.getStart())) return null;

				currentDbEntry = dbNsfpFile.next();
			}

			if (currentDbEntry != null) latestChromo = currentDbEntry.getChromosomeName();
		}
	}

	public Map<String, String> getFieldsType() {
		return fieldsType;

	}

	/**
	 * Initialize default values
	 */
	@Override
	public void init() {
		needsConfig = true;
		needsDb = true;
		dbTabix = true;
		dbType = "dbnsfp";

		fieldsToAdd = new HashMap<String, String>();
		fieldsType = new HashMap<String, String>();
		fieldsDescription = new HashMap<String, String>();
		annotateEmpty = false;
		collapseRepeatedValues = false;
	}

	/**
	 * Initialize annotation process
	 */
	public void initAnnotate() throws IOException {
		// Open VCF file
		vcfFile = new VcfFileIterator(vcfFileName);
		vcfFile.setDebug(debug);

		// Check and open dbNsfp
		dbNsfpFile = new DbNsfpFileIterator(dbFileName);
		dbNsfpFile.setCollapseRepeatedValues(collapseRepeatedValues);
		if (tabixCheck && !dbNsfpFile.isTabix()) fatalError("Tabix index not found for database '" + dbFileName + "'.\n\t\tSnpSift dbNSFP only works with tabix indexed databases, please create or download index.");

		// Guess data types
		dbNsfpFile.guessVcfTypes();
		VcfInfoType types[] = dbNsfpFile.getTypes();
		String fieldNames[] = dbNsfpFile.getFieldNamesSorted();
		for (int i = 0; i < fieldNames.length; i++) {
			String type = (types[i] != null ? types[i].toString() : "String");
			fieldsType.put(fieldNames[i], type);
			fieldsDescription.put(fieldNames[i], "Field '" + fieldNames[i] + "' from dbNSFP");
		}

		currentDbEntry = null;

		// No field names specified? Use default
		if (fieldsNamesToAdd == null) fieldsNamesToAdd = DEFAULT_FIELDS_NAMES_TO_ADD;
		for (String fn : fieldsNamesToAdd.split(",")) {
			if (fieldsDescription.get(fn) == null) usage("Error: Field name '" + fn + "' not found");
			fieldsToAdd.put(fn, fieldsDescription.get(fn));
		}
	}

	/**
	 * Are all values empty?
	 * @param values
	 * @return
	 */
	boolean isDbNsfpValueEmpty(String values) {
		// Single value check
		if (values == null || values.isEmpty() || values.equals(".")) return true;

		// Multiple values? Are all of them empty?
		for (String val : values.split(","))
			if (!val.isEmpty() && !val.equals(".")) return false;

		return true;
	}

	/**
	 * Parse command line arguments
	 */
	@Override
	public void parse(String[] args) {
		if (args.length == 0) usage(null);

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];

			if (arg.equals("-a")) annotateEmpty = true;
			else if (arg.equals("-f")) fieldsNamesToAdd = args[++i]; // Filed to be used
			else if (arg.equalsIgnoreCase("-noCollapse")) collapseRepeatedValues = false;
			else if (arg.equalsIgnoreCase("-collapse")) collapseRepeatedValues = true;
			else {
				if (vcfFileName == null) vcfFileName = arg;
				else usage("Unknown extra parameter '" + arg + "'");
			}
		}

		// Sanity check
		if (vcfFileName == null) usage("Missing 'file.vcf'");
	}

	@Override
	public void run() {
		run(false);
	}

	public List<VcfEntry> run(boolean createList) {
		// Read config
		if (config == null) loadConfig();

		// Find or download database
		dbFileName = databaseFindOrDownload();

		if (verbose) Timer.showStdErr("Annotating\n" //
				+ "\tInput file    : '" + vcfFileName + "'\n" //
				+ "\tDatabase file : '" + dbFileName + "'" //
		);

		return annotate(createList);
	}

	public void setTabixCheck(boolean tabixCheck) {
		this.tabixCheck = tabixCheck;
	}

	/**
	 * Show usage message
	 * @param msg
	 */
	@Override
	public void usage(String msg) {
		if (msg != null) {
			System.err.println("Error: " + msg);
			showCmd();
		}

		StringBuilder sb = new StringBuilder();
		for (String f : DEFAULT_FIELDS_NAMES_TO_ADD.split(","))
			sb.append("\t                - " + f + "\n");

		showVersion();

		System.err.println("Usage: java -jar " + SnpSift.class.getSimpleName() + ".jar " + command + " [options] file.vcf > newFile.vcf\n" //
				+ "Options:\n" //
				+ "\t-a            : Annotate fields, even if the database has an empty value (annotates using '.' for empty).\n" //
				+ "\t-collapse     : Collapse repeated values from dbNSFP. Default: " + collapseRepeatedValues + "\n" //
				+ "\t-noCollapse   : Switch off 'collapsing' repeated values from dbNSFP. Default: " + !collapseRepeatedValues + "\n" //
				+ "\t-f            : A comma separated list of fields to add.\n" //
				+ "\t                Default fields to add:\n" + sb //
		);

		usageGenericAndDb();

		System.err.println("Note: Databse (dbNSFP.txt.gz) must be bgzip and tabix indexed file.\n      The corresponding index file (dbNSFP.txt.gz.tbi) must be present.\n");

		System.exit(1);
	}
}
