import {Command} from 'app/markdown-editor/commands/command';

export abstract class SpecialCommand extends Command {
    buttonTitle: string;

    abstract execute(editor: any): void;
}
