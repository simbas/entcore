import { Component, Input,ChangeDetectorRef, ViewChild, OnInit } from '@angular/core'
import { ComponentDescriptor, DynamicComponent } from '../../directives'
import { BundlesService } from 'sijil'
import { MessageBox, MessageType,icons} from './message-box.component'

@Component({
    selector: 'message-sticker',
    template: `
    <i (click)="loadMessageBox()" class="fa {{icons[type]}} is-{{type}}"></i>
        <span message-box-anchor>
            <ng-container [dynamic-component]="newMessageBox()"></ng-container>
        </span>
        `,
    styles: [`
        :host {
            display: inline;
            padding-left : .2em;
        }
        span[message-box-anchor] {
            position: absolute;
        }
        i { cursor : pointer; }
    `]
})
export class MessageSticker implements OnInit {
    constructor (
        private cdRef:ChangeDetectorRef)  {}

    @Input() type: MessageType;
    @Input() header:string;
    @Input() messages:(string | [string,Object])[];
    @ViewChild(DynamicComponent) dComponent: DynamicComponent;

    readonly icons = icons;
    
    ngOnInit():void { 
        if (this.type == undefined) {
            throw new Error('MessageSticker : type\' property must be set');
        }
    }

    newMessageBox():ComponentDescriptor {
        return new ComponentDescriptor(MessageBox, {
            type: this.type,
            header: this.header, 
            messages:this.messages,
            position:'absolute'
        });
    }

    loadMessageBox() : void {
        this.dComponent.load();
    }

}