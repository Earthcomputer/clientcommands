# Contributing
## In short
1. Clone the repository
   ```
   git clone https://github.com/Earthcomputer/clientcommands
   cd clientcommands
   ```
1. Generate the Minecraft source code
   ```
   ./gradlew genSources
   ```
    - Note: on Windows, use `gradlew` rather than `./gradlew`.
1. Import the project into your preferred IDE.
    1. If you use IntelliJ (the preferred option), you can simply import the project as a Gradle project.
    1. If you use Eclipse, you need to `./gradlew eclipse` before importing the project as an Eclipse project.
1. Edit the code
1. After testing in the IDE, build a JAR to test whether it works outside the IDE too
   ```
   ./gradlew build
   ```
   The mod JAR may be found in the `build/libs` directory
1. [Create a pull request](https://help.github.com/en/articles/creating-a-pull-request)
   so that your changes can be integrated into clientcommands
    - Note: for large contributions, create an issue before doing all that
      work, to ask whether your pull request is likely to be accepted
## Scope
clientcommands is not a hacked or cheat client. Commands or utilities whose sole purpose is to provide an unfair advantage over other players are out of scope for clientcommands.

That said, commands which are only needed in very specific circumstances are also out of scope. clientcommands consists of commands that are generally useful. For edge cases there is the [scripting API](https://github.com/Earthcomputer/clientcommands/blob/fabric/docs/clientcommands.ts) contained within this mod.
## Code style
Please adhere to these styling instructions when contributing. If you don't do this initially you will be requested to change your commits accordingly.
These instructions are not foundational. You're expected to have an understanding of what is clean Java code and what isn't. These instructions build on general consensus.

You may see old code that doesn't adhere to these guidelines, but all new code must do. If you are working nearby old code, you can update it to fit the guidelines if you want to.
### Variables
#### Naming
* Use descriptive variable names everywhere
* Loop indices can be an exception to the above instruction
   * Never use `j`. If you find yourself wanting to use `j`, it's a sign that you should rename your loop variables to something more descriptive. You can use `i` but no more than that
   * Loops with small bodies (not more than a few lines) may use `i` as the loop variable, unless there is something obvious that's more descriptive
   * Loops with large bodies may only use `i` if the loop variable is unused within the loop body
#### Immutable constants
* Immutable constants should use UPPER_SNAKE_CASE
* Mark immutable constants as `private static final`
### Statements
#### `if`, `for` and `while` statements
* `if`, `for` and `while` statements should always use braces
* There should be a space between the keyword and the statement-bracket
#### `import` statements
* `import static`s should always use wildcard imports
   * They should be placed after the other imports
   * Don't use `import static` for anything else than commands
* Use import statements rather than fully qualified class names
### Command registry
#### General
* Execute a command's code mostly in a dedicated method, rather than in the `register` method
* If the command was successful and there isn't something sensible to return, it should `return 0`. If the command was not successful, an exception should be thrown
#### Exceptions
* Exception variables are [immutable constants](#immutable-constants)
### Miscellaneous
* Any message the player may receive should be translated
    * Except for debugging (log messages, debug HUD)
* Only use up to Java 8 in your code
* All files should have a newline at the end of the file
* Do not use AWT at all
