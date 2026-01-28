// Fichier racine du projet, qui configure les dépendances communes

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
