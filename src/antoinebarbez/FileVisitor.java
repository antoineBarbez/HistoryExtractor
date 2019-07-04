package antoinebarbez;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public class FileVisitor extends ASTVisitor {
	private List<String> classes = new ArrayList<String>();
	private Map<String, String> classToBodyMap = new HashMap<String, String>();
	
	public List<String> getClasses() {
		return this.classes;
	}
	
	public Map<String, String> getClassToBodyMap() {
		return this.classToBodyMap;
	}
	
	@Override
	public boolean visit(TypeDeclaration node) {
		//Ignore inner classes, they will be processed by the ClassVisitor.
		if (node.isMemberTypeDeclaration()) {
			return false;
		}
		
		String className = node.resolveBinding().getQualifiedName();
		classes.add(className);
		classToBodyMap.put(className, node.toString());
		return true;
	}
}
