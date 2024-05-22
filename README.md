# Projet de Minage Distribué

Ce projet implémente un système de minage distribué où un serveur coordonne plusieurs workers pour résoudre des tâches de minage.
Chaque worker tente de trouver un nonce qui, lorsqu'il est combiné avec des données spécifiques,
produit un hash SHA-256 commençant par un certain nombre de zéros (la difficulté).

## Utilisation

1. Accédez au répertoire `src` 
    ```sh
    cd src
    ```

2. Compilez les fichiers `LauncherServeur.java` et `LauncherWorker.java` 
    ```sh
    javac LauncherServeur.java
    javac LauncherWorker.java
    ```

3. Lancez le serveur 
    ```sh
    java LauncherServeur
    ```

4. Lancez le nombre souhaité de workers 
    ```sh
    java LauncherWorker
    ```

## Commandes Disponibles

Une fois que le serveur et les workers sont en cours d'exécution, vous pouvez utiliser les commandes suivantes 

- `SOLVE d` : Lance une tâche de minage avec une difficulté spécifiée.
- `PROGRESS` : Demande l'état de progression de chaque worker. Les workers peuvent répondre de deux manières 
- `CANCEL` : Annule la tâche en cours.
- `quit` : Termine le serveur et les workers.
- `help` : Affiche la liste des commandes ainsi qu'une brève description.


## Auteurs

- REITER Romain
- Chelh Yacine
