# HistoryExtractor

HistoryExtractor is a project that allows to extract the change history of a *java* project, similarly to the *git log* command but at a class-level granularity.

The HistoryExtractor takes 3 arguments:
1. The path of the *Git* repository
2. The *SHA* of the start commit
3. The path of the output file

The output of the HistoryExtractor is a *.csv* file whose rows contain the *SHA* of the commit, the name of the class that have been changed and the change type (A, M, D).