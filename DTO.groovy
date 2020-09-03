import com.intellij.database.model.DasTable
import com.intellij.database.model.ObjectKind
import com.intellij.database.util.Case
import com.intellij.database.util.DasUtil
import java.io.*
import java.text.SimpleDateFormat

/*
 * Available context bindings:
 *   SELECTION   Iterable<DasObject>
 *   PROJECT     project
 *   FILES       files helper
 */
packageName = ""
typeMapping = [
        (~/(?i)tinyint|smallint|mediumint/)      : "Integer",
        (~/(?i)int/)                             : "Long",
        (~/(?i)bool|bit/)                        : "Boolean",
        (~/(?i)float|double|decimal|real/)       : "Double",
        (~/(?i)datetime|timestamp|date|time/)    : "Timestamp",
        (~/(?i)blob|binary|bfile|clob|raw|image/): "InputStream",
        (~/(?i)/)                                : "String"
]

FILES.chooseDirectoryAndSave("Choose directory", "Choose where to store generated files") { dir ->
    SELECTION.filter { it instanceof DasTable && it.getKind() == ObjectKind.TABLE }.each { generate(it, dir) }
}

def generate(table, dir) {
    //根据表名生成类名
    def className = javaClassName(table.getName(), true)+"DTO"
    //def className = javaName(table.getName(), true)
    def fields = calcFields(table)
    packageName = getPackageName(dir)
    PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(dir, className + ".java")), "UTF-8"))
    printWriter.withPrintWriter {out -> generate(out, className, fields,table)}
//    new File(dir, className + ".java").withPrintWriter { out -> generate(out, className, fields,table) }
}

// 获取包所在文件夹路径
def getPackageName(dir) {
    return dir.toString().replaceAll("\\\\", ".").replaceAll("/", ".").replaceAll("^.*src(\\.main\\.java\\.)?", "") + ";"
}

def generate(out, className, fields,table) {
    def tableName = table.getName()
    out.println "package $packageName"
    out.println ""
    out.println "import io.swagger.annotations.ApiModel;"
    out.println "import io.swagger.annotations.ApiModelProperty;"
    out.println "import javax.validation.constraints.NotNull;"  
    out.println "import javax.validation.constraints.Max;"
    out.println "import javax.validation.constraints.Min;"
    out.println "import javax.validation.constraints.NotBlank;"
    out.println "import org.hibernate.validator.constraints.Length;"
    out.println "import java.io.Serializable;"
	out.println "import lombok.Data;"
				
    Set types = new HashSet()

    fields.each() {
        types.add(it.type)
    }

    if (types.contains("Date")) {
        out.println "import java.util.Date;"
    }

    if (types.contains("InputStream")) {
        out.println "import java.io.InputStream;"
    }
    out.println ""
    out.println "/**\n" +
            " * <>"+tableName+"表-DTO类</> \n" +
            " * @author <>liaoxinyi</> \n" +
            " * @date ["+ new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "] \n" +
            " * @since <>V1.0</> \n" +
            " */"
    out.println ""
    out.println "@Data"
	out.println "@ApiModel()"
    out.println "public class $className  implements Serializable {"
    out.println ""


    fields.each() {
        out.println ""
        // 输出注释
        if (isNotEmpty(it.commoent)) {
            commoentStr=it.commoent.toString()
        }else{
            commoentStr="XXX"
        }
        out.println "\t/**"
        out.println "\t * ${commoentStr}"
        out.println "\t */"
        //输出swagger内容和校验内容
            if ("Boolean".equals(it.type)){
                out.println "\t@ApiModelProperty(value = \"${commoentStr}\", required = true, example = \"XXX\")"
                out.println "\t@NotNull(message = \"${it.name}不能为空\")"
            }
            if ("String".equals(it.type)){
                out.println "\t@ApiModelProperty(value = \"${commoentStr}\", required = true,dataType = "string",allowMultiple = false, example = \"XXX\")"
                out.println "\t@NotBlank(message = \"${it.name}不能为空\")"
                out.println "\t@Size(min = 1,max = 255)"
            }            
            if ("Long".equals(it.type) || "Double".equals(it.type)){
                out.println "\t@ApiModelProperty(value = \"${commoentStr}\", required = true,dataType = "int",allowMultiple = false, example = \"1\")"
                out.println "\t@Min(0)"
                out.println "\t@Max(Integer.MAX_VALUE)"
            }
            if ("Timestamp".equals(it.type)){
                out.println "\t@NotNull(message = \"${it.name}不能为空\")"
            }            
            out.println "\t@ApiModelProperty(value = \"${commoentStr}\",required = true,allowMultiple = false, example = \"XXX\")"
        
        //if (it.annos != "") out.println "${it.annos}"

        // 输出成员变量
        out.println "\tprivate ${it.type} ${it.name};"
    }
    
        //tostring方法
    out.println ""
    out.println "\t/**"
    out.println "\t * 重写tostring方法为alibaba格式的json"
    out.println "\t */"
    out.println "\tpublic String toString(){"
    out.println "\treturn JSON.toJSONString(this);"
    out.println "}"
    

    // 输出get/set方法
//    fields.each() {
//        out.println ""
//        out.println "\tpublic ${it.type} get${it.name.capitalize()}() {"
//        out.println "\t\treturn this.${it.name};"
//        out.println "\t}"
//        out.println ""
//
//        out.println "\tpublic void set${it.name.capitalize()}(${it.type} ${it.name}) {"
//        out.println "\t\tthis.${it.name} = ${it.name};"
//        out.println "\t}"
//    }
    out.println ""
    out.println "}"
}

def calcFields(table) {
    DasUtil.getColumns(table).reduce([]) { fields, col ->
        def spec = Case.LOWER.apply(col.getDataType().getSpecification())

        def typeStr = typeMapping.find { p, t -> p.matcher(spec).find() }.value
        def comm =[
                colName : col.getName(),
                name :  javaName(col.getName(), false),
                type : typeStr,
                commoent: col.getComment(),
                annos: "\t@Column(name = \""+col.getName()+"\" )"]
        if("pk".equals(Case.LOWER.apply(col.getName().substring(0,2)))){
            comm.annos ="\t@Id"
            comm.name ="id"
        }
        fields += [comm]
    }
}

// 处理类名（这里是因为我的表都是以tb_命名的，所以需要处理去掉生成类名时的开头的T，
// 如果你不需要那么请查找用到了 javaClassName 这个方法的地方修改为 javaName 即可）
def javaClassName(str, capitalize) {
    def s = com.intellij.psi.codeStyle.NameUtil.splitNameIntoWords(str)
            .collect { Case.LOWER.apply(it).capitalize() }
            .join("")
            .replaceAll(/[^\p{javaJavaIdentifierPart}[_]]/, "_")
    if("tb".equals(s.substring(0,2))){
        // 去除开头的tb
        s = s.substring(2)
    }
    if("t".equals(s.substring(0,1))){
        // 去除开头的t
        s = s.substring(1)
    }
    capitalize || s.length() == 1? s : Case.LOWER.apply(s[0]) + s[1..-1]
}

def javaName(str, capitalize) {
//    def s = str.split(/(?<=[^\p{IsLetter}])/).collect { Case.LOWER.apply(it).capitalize() }
//            .join("").replaceAll(/[^\p{javaJavaIdentifierPart}]/, "_")
//    capitalize || s.length() == 1? s : Case.LOWER.apply(s[0]) + s[1..-1]
    def s = com.intellij.psi.codeStyle.NameUtil.splitNameIntoWords(str)
            .collect { Case.LOWER.apply(it).capitalize() }
            .join("")
            .replaceAll(/[^\p{javaJavaIdentifierPart}[_]]/, "_")
    capitalize || s.length() == 1? s : Case.LOWER.apply(s[0]) + s[1..-1]
}

def isNotEmpty(content) {
    return content != null && content.toString().trim().length() > 0
}

static String changeStyle(String str, boolean toCamel){
    if(!str || str.size() <= 1)
        return str

    if(toCamel){
        String r = str.toLowerCase().split('_').collect{cc -> Case.LOWER.apply(cc).capitalize()}.join('')
        return r[0].toLowerCase() + r[1..-1]
    }else{
        str = str[0].toLowerCase() + str[1..-1]
        return str.collect{cc -> ((char)cc).isUpperCase() ? '_' + cc.toLowerCase() : cc}.join('')
    }
}

static String genSerialID()
{
    return "\tprivate static final long serialVersionUID =  "+Math.abs(new Random().nextLong())+"L;"
}
