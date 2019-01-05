import { Command } from './command';
import { Ace } from 'ace-builds';
import SearchOptions = Ace.SearchOptions;

export class ItalicCommand extends Command {
    buttonTitle = 'Italic';
    buttonIcon = 'fa fa-italic';

    execute(editor: any): void {
        let chosenText = editor.getSelectedText();
        const textToAdd = `*${chosenText}*`;
        editor.clearSelection();
        editor.moveCursorTo(editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        const search: SearchOptions = {
            needle: chosenText,
            preventScroll: true,
            backwards: true,
            start: null,
            skipCurrent: false,
            range: null,
            preserveCase: false,
            regExp: chosenText,
            wholeWord: null,
            caseSensitive: false,
            wrap: false
        };
        editor.replace(textToAdd, search);
    }
}
