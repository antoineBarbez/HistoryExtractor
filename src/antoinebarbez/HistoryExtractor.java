package antoinebarbez;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.NullOutputStream;

public class HistoryExtractor {
	public static void main(String[] args) throws Exception {
		if (args.length != 3) {
			throw new IllegalArgumentException("Illegal Arguments.");
		}
		
		extractHistory(args[0], args[1], args[2]);
	}
	
	private static void extractHistory(String projectDir, String sha, String outputFile) throws Exception {
		try (Git git = openRepository(projectDir)) {
			git.checkout().setName(sha).call();
			
			// Retrieve all prior commits
	        ObjectId head = git.getRepository().resolve(Constants.HEAD);
	        Iterator<RevCommit> iteratorOnCommits = git.log().add(head).call().iterator();
	        
	        try (PrintWriter out = new PrintWriter(outputFile)) {
				out.println("Snapshot;File;ChangeType");
				
				RevCommit currentCommit = iteratorOnCommits.next();
		        while(iteratorOnCommits.hasNext()) {
					RevCommit previousCommit = iteratorOnCommits.next();
					
					analyzeDiff(git, out, previousCommit, currentCommit);
					
					currentCommit = previousCommit;
				}
			}
		}
		
	}
	
	private static void analyzeDiff(Git git, PrintWriter out, RevCommit previousCommit, RevCommit currentCommit) throws IOException {
		try (DiffFormatter diffFormatter = new DiffFormatter(NullOutputStream.INSTANCE)) {
            diffFormatter.setRepository(git.getRepository());

            final List<DiffEntry> diffs = diffFormatter.scan(previousCommit.getTree(), currentCommit.getTree());
            
            for (DiffEntry diff : diffs) {
            	String currentPath = diff.getNewPath();
    			String previousPath = diff.getOldPath();
                switch (diff.getChangeType()) {
                case ADD:
                	if(currentPath.endsWith(".java")) {
                		List<String> classes = getClasses(getFileContent(git, currentCommit, currentPath));
                		for (String c: classes) {
                			out.println(currentCommit.name() + ";" + c + ";" + "A");
                		}
                	}
                    break;
                case MODIFY:
                	if(currentPath.endsWith(".java")) {
                		String previousFileContent = getFileContent(git, previousCommit, previousPath);
                		String currentFileContent = getFileContent(git, currentCommit, currentPath);
                		Map<String, String> classChanges = getClassChanges(previousFileContent, currentFileContent);
                		for (Map.Entry<String, String> entry: classChanges.entrySet()) {
                			out.println(currentCommit.name() + ";" + entry.getKey() + ";" + entry.getValue());
                		}
                	}
                	break;
                case DELETE:
                	if(previousPath.endsWith(".java")) {
                		List<String> classes = getClasses(getFileContent(git, previousCommit, previousPath));
                		for (String c: classes) {
                			out.println(currentCommit.name() + ";" + c + ";" + "D");
                		}
                	}
                    break;
                default:
                    break;
                }
            }
        }
	}
	
	private static String getFileContent(Git git, RevCommit commit, String path) throws IOException {
		  try (TreeWalk treeWalk = TreeWalk.forPath(git.getRepository(), path, commit.getTree())) {
		    ObjectId blobId = treeWalk.getObjectId(0);
		    try (ObjectReader objectReader = git.getRepository().newObjectReader()) {
		      ObjectLoader objectLoader = objectReader.open(blobId);
		      byte[] bytes = objectLoader.getBytes();
		      return new String(bytes, StandardCharsets.UTF_8);
		    }
		  }
		}
	
	private static FileVisitor getFileVisitor(String file) {
		ASTParser parser = ASTParser.newParser(AST.JLS11);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setCompilerOptions(JavaCore.getOptions());
		parser.setUnitName("file");
		
		parser.setEnvironment(null, null, null, true);
		parser.setSource(file.toCharArray());
		
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		
		FileVisitor visitor = new FileVisitor();
		cu.accept(visitor);
		
		return visitor;
	}
	
	private static List<String> getClasses(String file) {
		return getFileVisitor(file).getClasses();
	}
	
	private static Map<String, String> getClassChanges(String previousFile, String currentFile) {
		Map<String, String> previousClassBodies = getFileVisitor(previousFile).getClassToBodyMap();
		Map<String, String> currentClassBodies = getFileVisitor(currentFile).getClassToBodyMap();
		
		List<String> currentClasses = new ArrayList<String>(currentClassBodies.keySet());
		Map<String, String> classChanges = new HashMap<String, String>();
		for (String currentClass: currentClasses) {
			if (previousClassBodies.containsKey(currentClass)) {
				if (!currentClassBodies.get(currentClass).equals(previousClassBodies.get(currentClass))) {
					classChanges.put(currentClass, "M");
				}
				previousClassBodies.remove(currentClass);
				currentClassBodies.remove(currentClass);
			}
		}
		
		for (String currentClass: currentClassBodies.keySet()) {
			classChanges.put(currentClass, "A");
		}
		
		for (String previousClass: previousClassBodies.keySet()) {
			classChanges.put(previousClass, "D");
		}
		
		return classChanges;
	}
	
	public static Git openRepository(String projectDir) throws Exception {
	    File folder = new File(projectDir);
	    Repository repository;
	    if (folder.exists()) {
	        RepositoryBuilder builder = new RepositoryBuilder();
	        repository = builder
	            .setGitDir(new File(folder, ".git"))
	            .readEnvironment()
	            .findGitDir()
	            .build();
	    } else {
	        throw new FileNotFoundException(projectDir);
	    }
	    return new Git(repository);
	}
}
