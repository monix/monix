dependency-updates:
	@mkdir -p ~/.sbt/1.0/plugins && \
	( test -f ~/.sbt/1.0/plugins/sbt-updates.sbt || echo 'addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.4")' > ~/.sbt/1.0/plugins/sbt-updates.sbt ) && \
	sbt dependencyUpdatesReport 1>/dev/null && \
	sbt ";reload plugins;dependencyUpdatesReport" 1>/dev/null && \
	find . -name "dependency-updates.txt" -type f
