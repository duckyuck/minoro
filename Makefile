.PHONY: not-dirty jar pom update-pom release deploy

DATE := $(shell date +"%Y-%m-%d")
VERSION := ${DATE}${VERSION_SUFFIX}

not-dirty:
	@echo "Checking for uncomitted changes"
	test -z "$(shell git status --porcelain)"

jar:
	rm -rf target/
	clj -Atest
	clj -Ajar target/minoro-${VERSION}.jar

pom:
	clj -Spom

update-pom: pom not-dirty
	sed -i.bak  's|^  <version>.*</version>|  <version>${VERSION}</version>|g' pom.xml
	rm pom.xml.bak

update-readme:
	sed -E -i.bak 's/"[0-9]+[0-9]+[0-9]+[0-9]+-[0-9]+[0-9]+-[0-9]+[0-9]+.*"/"${VERSION}"/g' README.md
	rm README.md.bak

release: not-dirty jar update-pom update-readme
	git add pom.xml README.md
	git commit -m "Release ${VERSION}"
	git tag "v${VERSION}"

deploy: release
	clj -Adeploy target/minoro-${VERSION}.jar
