package prodwsdl

import groovy.io.FileType

def addAnnotationsToClass(file) {
    if (!file.exists() || !file.canRead()) {
        throw new IOException("File does not exist or is not readable: ${file}")
    }

    def lines = file.readLines()
    def newLines = []
    def classFound = false
    def hasData = false
    def hasJsonInclude = false
    def hasJsonProperty = false
    def importData = false
    def importJsonInclude = false
    def importJsonProperty = false
    def importDate = false // Track if java.util.Date import is already present
    def usesDate = false   // Track if Date type is found in the class
    def hasRoleField = false // Track if the role field exists

    // First pass: check existing annotations, imports, and handle replacements
    lines.each { line ->
        if (line.trim().startsWith("@Data")) {
            hasData = true
        }
        if (line.trim().startsWith("@JsonInclude(JsonInclude.Include.NON_EMPTY)")) {
            hasJsonInclude = true
        }
        if (line.trim().startsWith("import lombok.Data;")) {
            importData = true
        }
        if (line.trim().startsWith("import com.fasterxml.jackson.annotation.JsonInclude;")) {
            importJsonInclude = true
        }
        if (line.trim().startsWith("import com.fasterxml.jackson.annotation.JsonProperty;")) {
            importJsonProperty = true
        }
        if (line.trim().startsWith("import java.util.Date;")) {
            importDate = true // Track that java.util.Date import is already present
        }

        // Check if Date type is used in the class
        if (line.contains("Date ")) {
            usesDate = true // Mark that the class uses Date type
        }
        // Remove imports related to XMLGregorianCalendar and javax.xml.datatype.Date
        if (line.trim().startsWith("import javax.xml.datatype.XMLGregorianCalendar;") ||
            line.trim().startsWith("import javax.xml.datatype.Date;") ||
			line.trim().startsWith("import javax.xml.bind.annotation.XmlEnumValue;")) {
            // Skip adding this line to newLines to remove it
        } else {
            // Replace XMLGregorianCalendar with java.util.Date
            if (line.contains("XMLGregorianCalendar")) {
                line = line.replace("XMLGregorianCalendar", "Date")
                usesDate = true // Mark that the class now uses Date
            }

            // Replace @XmlEnumValue with @JsonProperty
            if (line.trim().startsWith("@XmlEnumValue")) {
                line = line.replace("@XmlEnumValue", "@JsonProperty") // Replace annotation
                hasJsonProperty = true // Track that @JsonProperty is now present
            }

            // Add @JsonProperty("content") after @XmlValue if not already present
            if (line.trim().contains("@XmlValue")) {
                newLines << line // Add the @XmlValue annotation first

                // Check if the next line already contains @JsonProperty("content")
                def nextLine = lines[lines.indexOf(line) + 1]?.trim()
                if (!nextLine?.contains('@JsonProperty("content")')) {
                    newLines << '@JsonProperty("content")' // Add @JsonProperty("content") if not present
                    hasJsonProperty = true // Track that @JsonProperty is added
                }
            } else {
                newLines << line
            }
        }
    }

    // Second pass: modify lines to add annotations and fields
    def finalLines = []
    newLines.eachWithIndex { line, index ->
        // Check for the protected int id; declaration
        if (line.trim() == 'protected int id;') {
            finalLines << line
            
            // Check if 'protected String role;' already exists
            if (!hasRoleField && !newLines.any { it.trim() == 'protected String role;' }) {
                finalLines << 'protected String role;'
                hasRoleField = true // Mark that the role field has been added
            }
        } else {
            // Add annotations if the class declaration is found
            if (line.trim().startsWith("public class") && !classFound) {
                // Add @Data if missing
                if (!hasData) {
                    finalLines << "@Data"
                    hasData = true // Track that @Data was added
                }
                // Add @JsonInclude if missing
                if (!hasJsonInclude) {
                    finalLines << "@JsonInclude(JsonInclude.Include.NON_EMPTY)"
                    hasJsonInclude = true // Track that @JsonInclude was added
                }
                finalLines << line
                classFound = true
            } else {
                finalLines << line
            }
        }
    }

    // Add missing imports, even if only the annotations were found and already existed
    def importLines = []
    if (hasData && !importData) {
        // If @Data exists but the import is missing, add it
        importLines << "import lombok.Data;"
    }
    if (hasJsonInclude && !importJsonInclude) {
        // If @JsonInclude exists but the import is missing, add it
        importLines << "import com.fasterxml.jackson.annotation.JsonInclude;"
    }
    if (hasJsonProperty && !importJsonProperty) {
        // If @JsonProperty was added and import is missing, add it
        importLines << "import com.fasterxml.jackson.annotation.JsonProperty;"
    }
    if (usesDate && !importDate) {
        // Add java.util.Date import only if the Date type is used
        importLines << "import java.util.Date;"
    }

    // Insert import statements after the package declaration
    if (!importLines.isEmpty()) {
        def finalWithImports = []
        finalLines.each { line ->
            if (line.trim().startsWith("package")) {
                finalWithImports << line
                finalWithImports.addAll(importLines)
            } else {
                finalWithImports << line
            }
        }
        finalLines = finalWithImports
    }

    // Write the modified lines back to the file
    file.withWriter { writer ->
        finalLines.each { writer.println(it) }
    }
}

def directory = new File("C:/Users/narasp/Downloads/truckbom/tbv-cloud-azure/tbv/tbv-tbf-api/src/main/java/com/daimler/tbv/tbf/productionData/api/")

directory.eachFileRecurse(FileType.FILES) { file ->
    if (file.name.endsWith(".java")) {
        addAnnotationsToClass(file)
    }
}

println "Annotations, replacements, and imports added successfully!"
