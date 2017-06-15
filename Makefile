POMS := $(shell find * -name pom.xml ! -path '*/target/*' ! -path '*/src/*')
MAVEN_MODULES := $(patsubst %/pom.xml,%,$(filter-out pom.xml,$(POMS)))
GRADLE_FILES := $(shell find * -name build.gradle -o -name settings.gradle -o -name gradle.properties)
GRADLE_MODULES := $(patsubst %/build.gradle,%,$(filter %/build.gradle,$(GRADLE_FILES)))
MODULES := $(MAVEN_MODULES) $(GRADLE_MODULES)

MVN_WORKSPACE := .maven-workspace
MVN_CACHE := .maven-cache

MVN := mvn --settings "$(CURDIR)/settings.xml" -Dworkspace="$(CURDIR)/$(MVN_WORKSPACE)" -Dcache="$(CURDIR)/$(MVN_CACHE)" \
           -Dorg.ops4j.pax.url.mvn.localRepository="$(CURDIR)/$(MVN_WORKSPACE)" \
           -Dorg.daisy.org.ops4j.pax.url.mvn.settings="$(CURDIR)/settings.xml"
GRADLE := M2_HOME=$(CURDIR)/.gradle-settings $(CURDIR)/libs/dotify/dotify.api/gradlew -Dworkspace="$(CURDIR)/$(MVN_WORKSPACE)" -Dcache="$(CURDIR)/$(MVN_CACHE)"

MVN_LOG := tee -a $(CURDIR)/maven.log | cut -c1-1000 | pcregrep -M "^\[INFO\] -+\n\[INFO\] Building .*\n\[INFO\] -+$$|^\[(ERROR|WARNING)\]"; \
           test $${PIPESTATUS[0]} -eq 0

SHELL := /bin/bash

rwildcard = $(shell find $1 -type f | sed 's/ /\\ /g')
# does not support spaces in file names:
#rwildcard = $(foreach d,$(wildcard $1*),$(call rwildcard,$d/,$2) $(filter $(subst *,%,$2),$d))

.PHONY : all
all : check dist

.PHONY : dist
dist: dist-dmg dist-exe dist-zip-linux dist-deb dist-rpm dist-webui-deb dist-webui-rpm

.PHONY : dist-dmg
dist-dmg : assembly/.dependencies | $(MVN_WORKSPACE)
	cd assembly && \
	$(MVN) clean package -Pmac | $(MVN_LOG)
	mv assembly/target/*.dmg .

.PHONY : dist-exe
dist-exe : assembly/.dependencies | $(MVN_WORKSPACE)
	cd assembly && \
	$(MVN) clean package -Pwin | $(MVN_LOG)
	mv assembly/target/*.exe .

.PHONY : dist-zip-linux
dist-zip-linux : assembly/.dependencies | $(MVN_WORKSPACE)
	cd assembly && \
	$(MVN) clean package -Plinux | $(MVN_LOG)
	mv assembly/target/*.zip .

.PHONY : dist-deb
dist-deb : assembly/.dependencies | $(MVN_WORKSPACE)
	cd assembly && \
	$(MVN) clean package -Pdeb | $(MVN_LOG)
	mv assembly/target/*.deb .

.PHONY : dist-rpm
dist-rpm : assembly/.dependencies | $(MVN_WORKSPACE)
	if [ -f /etc/redhat-release ]; then \
		cd assembly && \
		$(MVN) clean package -Prpm | $(MVN_LOG) && \
		mv assembly/target/rpm/pipeline2/RPMS/*/*.rpm .; \
	else \
		echo "Skipping RPM because not running RedHat/CentOS"; \
	fi

.PHONY : dist-webui-deb
dist-webui-deb : assembly/.dependencies
	# see webui README for instructions on how to make a signed package for distribution
	cd webui && \
	./activator clean debian:packageBin | $(MVN_LOG)
	mv webui/target/*deb .

.PHONY : dist-webui-rpm
dist-webui-rpm : assembly/.dependencies
	# see webui README for instructions on how to make a signed package for distribution
	cd webui && \
	./activator clean rpm:packageBin
	mv webui/target/rpm/RPMS/noarch/*.rpm .

.PHONY : run
run : assembly/target/dev-launcher/bin/pipeline2
	$<

.PHONY : run-gui
run-gui : assembly/target/dev-launcher/bin/pipeline2
	$< gui

.PHONY : run-webui
run-webui : # webui/.dependencies
	if [ ! -d webui/dp2webui ]; then cp -r webui/dp2webui-cleandb webui/dp2webui ; fi
	cd webui && \
	./activator run

.PHONY : check
check : $(addprefix check-,$(MODULES))

.PHONY : $(addprefix check-,$(MODULES))
$(addprefix check-,$(MODULES)) : check-% : %/.last-tested

assembly/target/dev-launcher/bin/pipeline2 : assembly/.dependencies | $(MVN_WORKSPACE)
	cd assembly && \
	$(MVN) clean package -Pdev-launcher | $(MVN_LOG)
	rm assembly/target/dev-launcher/etc/*windows*
	if [ "$$(uname)" == Darwin ]; then \
		rm assembly/target/dev-launcher/etc/*linux*; \
	else \
		rm assembly/target/dev-launcher/etc/*mac*; \
	fi

ifneq ($(MAKECMDGOALS), clean)
-include $(addsuffix /.build.mk,$(MODULES))
endif

SAXON := $(MVN_WORKSPACE)/net/sf/saxon/Saxon-HE/9.4/Saxon-HE-9.4.jar

$(addsuffix /.build.mk,$(MAVEN_MODULES)) : .maven-build.mk
	if ! test -e $@; then \
		if cat .maven-modules | grep -Fx "$$(dirname $@)" >/dev/null; then \
			echo "\$$(error $@ could not be generated)" >$@; \
		fi \
	fi
	touch $@

.SECONDARY : .maven-build.mk
.maven-build.mk : .effective-pom.xml | $(SAXON)
	rm -f $(addsuffix /.build.mk,$(MAVEN_MODULES)) && \
	if ! java -cp $(SAXON) net.sf.saxon.Transform \
	          -s:$< \
	          -xsl:.make/make-maven-build.mk.xsl \
	          CURDIR="$(CURDIR)" \
	          MODULE="." \
	          SRC_DIR="$$(cat .maven-modules | while read -r m; do test -e $$m/src && echo $$m/src; done | paste -sd ' ' -)" \
	          MAIN_DIR="$$(cat .maven-modules | while read -r m; do test -e $$m/src/main && echo $$m/src/main; done | paste -sd ' ' -)" \
	          OUTPUT_FILENAME=".build.mk" \
	          >/dev/null \
	; then \
		rm -f $(addsuffix /.build.mk,$(MAVEN_MODULES)) && \
		exit 1; \
	fi

$(SAXON) :
	# cd into random directory in order to force Maven "stub" project
	cd .make && \
	$(MVN) org.apache.maven.plugins:maven-dependency-plugin:3.0.0:get -Dartifact=net.sf.saxon:Saxon-HE:9.4:jar

# the purpose of the test is for making "make -B" not affect this rule (to speed thing up)
# can not use $^ because it includes .dependencies-init
# .maven-modules omitted because it has no additional prerequisites
.effective-pom.xml : .maven-modules $(POMS) | $(MVN_WORKSPACE)
	if ! find $@ $(POMS) >/dev/null 2>/dev/null || [[ -n $$(find $(POMS) -newer $@ 2>/dev/null) ]]; then \
		cat $< | while read -r module; do \
			pom=$$module/pom.xml && \
			v=$$(xmllint --xpath "/*/*[local-name()='version']/text()" $$pom) && \
			g=$$(xmllint --xpath "/*/*[local-name()='groupId']/text()" $$pom 2>/dev/null) || \
			g=$$(xmllint --xpath "/*/*[local-name()='parent']/*[local-name()='groupId']/text()" $$pom) && \
			a=$$(xmllint --xpath "/*/*[local-name()='artifactId']/text()" $$pom) && \
			dest="$(MVN_WORKSPACE)/$$(echo $$g |tr . /)/$$a/$$v/$$a-$$v.pom" && \
			if [[ ! -e "$$dest" ]] || [[ -n $$(find "$$pom" -newer "$$dest" 2>/dev/null) ]]; then \
				mkdir -p $$(dirname $$dest) && \
				cp $$pom $$dest; \
			fi \
		done && \
		$(MVN) --quiet --projects $$(cat $< |paste -sd , -) help:effective-pom -Doutput=$(CURDIR)/$@; \
	else \
		touch $@; \
	fi

.maven-modules : $(POMS)
	function print_modules_recursively() { \
		local module=$$1 && \
		submodules=($$(xmllint --format --xpath "/*/*[local-name()='modules']/*" $$module/pom.xml 2>/dev/null \
		               | sed -e 's/<module>\([^<]*\)<\/module>/\1 /g')) && \
		if [[ $${#submodules[*]} -gt 0 ]]; then \
			for sub in $${submodules[*]}; do \
				if [ $$module == "." ]; then \
					print_modules_recursively $$sub; \
				else \
					print_modules_recursively $$module/$$sub; \
				fi \
			done \
		else \
			echo $$module; \
		fi \
	} && \
	print_modules_recursively . >$@

$(addsuffix /.build.mk,$(GRADLE_MODULES)) : $(GRADLE_FILES)
	module=$$(dirname $@) && \
	bash .make/make-gradle-build.mk.sh $$module >$@

.SECONDARY : cli/.install.zip
cli/.install.zip : cli/.install

cli/.install : cli/cli/*.go

updater/cli/.install : updater/cli/*.go

libs/jstyleparser/.install-sources.jar : libs/jstyleparser/.install

.gradle-settings/conf/settings.xml : settings.xml
	mkdir -p $(dir $@)
	cp $< $@

$(addsuffix /.build.mk,$(MODULES)) .maven-build.mk .effective-pom.xml .maven-modules : .dependencies-init

.SECONDARY : .dependencies-init
.dependencies-init :
	echo "Recomputing dependencies between modules..." >&2

# the purpose of the test is for making "make -B" not affect this rule (to speed thing up)
$(MVN_WORKSPACE) :
	if ! [ -e $(MVN_WORKSPACE) ]; then \
		mkdir -p $(MVN_CACHE) && \
		cp -r $(MVN_CACHE) $@; \
	fi

.PHONY : cache
cache :
	if [ -e $(MVN_WORKSPACE) ]; then \
		echo "Caching downloaded artifacts..." >&2 && \
		rm -rf $(MVN_CACHE) && \
		rsync -mr --exclude "*-SNAPSHOT" --exclude "maven-metadata-*.xml" $(MVN_WORKSPACE)/ $(MVN_CACHE); \
	fi

TEMP_REPOS := modules/scripts/dtbook-to-daisy3/target/test/local-repo

.PHONY : go-offline
go-offline :
	if [ -e $(MVN_WORKSPACE) ]; then \
		for repo in $(TEMP_REPOS); do \
			if [ -e $$repo ]; then \
				rsync -mr --exclude "*-SNAPSHOT" --exclude "maven-metadata-*.xml" $$repo/ $(MVN_WORKSPACE); \
			fi \
		done \
	fi

.PHONY : clean
clean : cache
	rm -rf $(MVN_WORKSPACE)
	rm -f maven.log
	rm -f *.zip *.deb *.rpm
	rm -rf webui/dp2webui
	rm -f .effective-pom.xml .maven-modules
	find * -name .last-tested -exec rm -r "{}" \;
	find * -name .build.mk -exec rm -r "{}" \;
	# generated in previous versions:
	rm -f .maven-build.mk
	find * -name .maven-to-install -exec rm -r "{}" \;
	find * -name .maven-to-test -exec rm -r "{}" \;
	find * -name .maven-to-test-dependents -exec rm -r "{}" \;
	find * -name .maven-snapshot-dependencies -exec rm -r "{}" \;
	find * -name .maven-effective-pom.xml -exec rm -r "{}" \;
	find * -name .maven-dependencies-to-install -exec rm -r "{}" \;
	find * -name .maven-dependencies-to-test -exec rm -r "{}" \;
	find * -name .maven-dependencies-to-test-dependents -exec rm -r "{}" \;
	find * -name .gradle-to-test -exec rm -r "{}" \;
	find * -name .gradle-snapshot-dependencies -exec rm -r "{}" \;
	find * -name .gradle-dependencies-to-install -exec rm -r "{}" \;
	find * -name .gradle-dependencies-to-test -exec rm -r "{}" \;

.PHONY : gradle-clean
gradle-clean :
	$(GRADLE) clean

.PHONY : checked
checked :
	touch $(addsuffix /.last-tested,$(MODULES))

.PHONY : website
website : assembly/.dependencies
	cd website && make MVN_OPTS="--settings '$(CURDIR)/settings.xml' -Dworkspace='$(CURDIR)/$(MVN_WORKSPACE)' -Dcache='$(CURDIR)/$(MVN_CACHE)'"

.PHONY : serve-website
serve-website : assembly/.dependencies
	cd website && make MVN_OPTS="--settings '$(CURDIR)/settings.xml' -Dworkspace='$(CURDIR)/$(MVN_WORKSPACE)' -Dcache='$(CURDIR)/$(MVN_CACHE)'" serve

.PHONY : publish-website
publish-website : assembly/.dependencies
	cd website && make MVN_OPTS="--settings '$(CURDIR)/settings.xml' -Dworkspace='$(CURDIR)/$(MVN_WORKSPACE)' -Dcache='$(CURDIR)/$(MVN_CACHE)'" publish

.PHONY : clean-website
clean-website :
	cd website && make MVN_OPTS="--settings '$(CURDIR)/settings.xml' -Dworkspace='$(CURDIR)/$(MVN_WORKSPACE)' -Dcache='$(CURDIR)/$(MVN_CACHE)'" clean

.PHONY : dump-maven-cmd
dump-maven-cmd :
	echo $(MVN)

.PHONY : dump-gradle-cmd
dump-gradle-cmd :
	echo $(GRADLE)

.PHONY : help
help :
	echo "make all:"                                                                                                >&2
	echo "	Incrementally compile and test code and package into a DMG, a EXE, a ZIP (for Linux), a DEB and a RPM"  >&2
	echo "make check:"                                                                                              >&2
	echo "	Incrementally compile and test code"                                                                    >&2
	echo "make dist:"                                                                                               >&2
	echo "	Incrementally compile code and package into a DMG, a EXE, a ZIP (for Linux), a DEB and a RPM"           >&2
	echo "make dist-dmg:"                                                                                           >&2
	echo "	Incrementally compile code and package into a DMG"                                                      >&2
	echo "make dist-exe:"                                                                                           >&2
	echo "	Incrementally compile code and package into a EXE"                                                      >&2
	echo "make dist-zip-linux:"                                                                                     >&2
	echo "	Incrementally compile code and package into a ZIP (for Linux)"                                          >&2
	echo "make dist-deb:"                                                                                           >&2
	echo "	Incrementally compile code and package into a DEB"                                                      >&2
	echo "make dist-rpm:"                                                                                           >&2
	echo "	Incrementally compile code and package into a RPM"                                                      >&2
	echo "make dist-webui-deb:"                                                                                     >&2
	echo "	Compile Web UI and package into a DEB"                                                                  >&2
	echo "make dist-webui-rpm:"                                                                                     >&2
	echo "	Compile Web UI and package into a RPM"                                                                  >&2
	echo "make run:"                                                                                                >&2
	echo "	Incrementally compile code and run locally"                                                             >&2
	echo "make run-gui:"                                                                                            >&2
	echo "	Incrementally compile code and run GUI locally"                                                         >&2
	echo "make run-webui:"                                                                                          >&2
	echo "	Compile and run web UI locally"                                                                         >&2
	echo "make website:"                                                                                            >&2
	echo "	Build the website"                                                                                      >&2

ifndef VERBOSE
.SILENT:
endif
