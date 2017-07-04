import { Component, 
    OnDestroy, OnInit, ChangeDetectionStrategy, ChangeDetectorRef, OnChanges,
    Input, Output, ViewChild } from '@angular/core'
import { BundlesService } from 'sijil'
import { ActivatedRoute, Data, Router, NavigationEnd } from '@angular/router'
import { Subscription } from 'rxjs/Subscription'
import { routing } from '../../../../routing/routing.utils'
import { ImportCSVService } from './import-csv.service'
import { Wizard } from 'infra-components'

@Component({
    selector: 'import-csv',
    templateUrl : './import-csv.component.html',
    styles : [`
        .error { color : red; font-weigth: bold; }
    `]
})

export class ImportCSV implements OnInit, OnDestroy {

    constructor(
        private route: ActivatedRoute,
        private router:Router,
        private bundles: BundlesService,
        private cdRef: ChangeDetectorRef){}

    translate = (...args) => { return (<any> this.bundles.translate)(...args) }

    // Subscriberts
    private structureSubscriber: Subscription;
    private routerSubscriber:Subscription;

    @ViewChild(Wizard) wizardEl: Wizard;

    importInfos = {
        type:'CSV',
        structureId:'',
        structureExternalId:'',
        structureName:'',
        UAI:'',
        predelete: false,
        transition:false,
    }
    stepErrors = [];

    // TODO Load from server 
    columnsAvailables = [
        "title",
        "username",
        "lastName",
        "firstName",
        "address",
        "zipCode",
        "city",
        "country",
        "email",
        "homePhone",
        "mobile",
        "ignore",
        "classes"
    ]

    // Mock Data
    columnsMapping = {
        "Teacher": {
            "Civilité": "title",
            "Nom usage": "username",
            "Nom": "lastName",
            "Prénom": "firstName",
            "Adresse": "address",
            "CP": "zipCode",
            "Commune": "city",
            "Pays": "country",
            "Courriel": "email",
            "Téléphone domicile": "homePhone",
            "Téléphone portable": "mobile",
            "Directeur": "ignore",
            "Classe": "classes"
        },
        "Student": {
            "Nom Elève": "lastName",
            "Nom d'usage Elève": "username",
            "Prénom Elève": "firstName",
            "Date naissance": "birthDate",
            "Sexe": "gender",
            "Cp1": "zipCode",
            "Commune1": "city",
            "Pays1": "country",
            "Adresse2": "address2",
            "Cp2": "zipCode2",
            "Commune2": "city2",
            "Pays2": "country2",
            " Cycle": "sector",
            "Niveau": "level",
            "Classe": "classes",
            "Attestation fournie": "ignore",
            "Autorisations associations": "ignore",
            "Autorisations photos": "ignore",
            "Décision de passage": "ignore"
        }
    };
    columnsMappingProfiles : Array<String>;
 
    // Mock Data
    classesMapping = {
        "Teacher": {
            "2. GS Ecole maternelle Lozon": "2. GS Ecole maternelle Lozon",
            "4. CE1-CE2 école élémentaire Rémilly sur Lozon": "4. CE1-CE2 école élémentaire Rémilly sur Lozon",
            "1. PS-MS Ecole maternelle Lozon": "1. PS-MS Ecole maternelle Lozon",
            "6. CM1-CM2 école élémentaire Rémilly sur Lozon": "6. CM1-CM2 école élémentaire Rémilly sur Lozon",
            "3. CP-CE1 école élémentaire Rémilly sur Lozon": "3. CP-CE1 école élémentaire Rémilly sur Lozon"
        },
        "Relative": {
            "2. GS Ecole maternelle Lozon": "2. GS Ecole maternelle Lozon",
            "1. PS-MS Ecole maternelle Lozon": "1. PS-MS Ecole maternelle Lozon",
            "4. CE1-CE2 école élémentaire Rémilly sur Lozon": "4. CE1-CE2 école élémentaire Rémilly sur Lozon",
            "6. CM1-CM2 école élémentaire Rémilly sur Lozon": "6. CM1-CM2 école élémentaire Rémilly sur Lozon",
            "3. CP-CE1 école élémentaire Rémilly sur Lozon": "3. CP-CE1 école élémentaire Rémilly sur Lozon"
        },
        "Student": {
            "2. GS Ecole maternelle Lozon": "2. GS Ecole maternelle Lozon",
            "1. PS-MS Ecole maternelle Lozon": "1. PS-MS Ecole maternelle Lozon",
            "4. CE1-CE2 école élémentaire Rémilly sur Lozon": "4. CE1-CE2 école élémentaire Rémilly sur Lozon",
            "6. CM1-CM2 école élémentaire Rémilly sur Lozon": "6. CM1-CM2 école élémentaire Rémilly sur Lozon",
            "3. CP-CE1 école élémentaire Rémilly sur Lozon": "3. CP-CE1 école élémentaire Rémilly sur Lozon"
        },
        "dbClasses": [
            "1. PS-MS Ecole maternelle Lozon",
            "3. CP-CE1 école élémentaire Rémilly sur Lozon",
            "4. CE1-CE2 école élémentaire Rémilly sur Lozon",
            "2. GS Ecole maternelle Lozon",
            "6. CM1-CM2 école élémentaire Rémilly sur Lozon"
        ]
    };
    classesMappingProfiles : Array<String>;

    ngOnInit(): void {
        this.structureSubscriber = routing.observe(this.route, "data").subscribe((data: Data) => {
            if(data['structure']) {
                this.importInfos.structureId = data['structure'].id;
                this.importInfos.structureExternalId = data['structure'].externalId;
                this.importInfos.structureName = data['structure'].name;
                this.importInfos.UAI = data['structure'].UAI;
            
                this.cdRef.markForCheck()
            }
        })

        this.routerSubscriber = this.router.events.subscribe(e => {
            if(e instanceof NavigationEnd)
                this.cdRef.markForCheck()
        })
    }

    ngOnDestroy(): void {
    }

    // Clean all the component state
    cancel () {
        this.stepErrors = []; // need to acces to wizard activeStep
        this.cdRef.markForCheck();
    }

    nextStep(activeStep: Number) {
        switch(activeStep) {
            case 0 : this.getColumsMapping(); break;
            case 1 : this.updateColumnsMapping(); break;
            case 2 : break;
            case 3 : break;
            case 4 : break;
            case 5 : break;
            case 6 : break;
            case 7 : break;
            default : break;
        }
    }

    previousStep (activeStep: Number) {
        this.wizardEl.doPreviousStep();
    }

    // (change) on input[file]
    loadFile(event) {
        let files : FileList = event.target.files;  
        if (files.length == 1) {
            this.importInfos[event.target.name] = event.target.files[0];
        }
    }
    
    /*
    * Next Step operations
    */
    private depositCSVFiles() {
        ImportCSVService.uploadCSV(this.importInfos)
            .then(data => {
                if (data.error) {
                    this.stepErrors[0] = data.error;
                } else {
                    this.stepErrors[0] = null;
                    this.wizardEl.doNextStep();
                }
                this.cdRef.markForCheck();
            });
    }

    private getColumsMapping() {
        ImportCSVService.getColumnsMapping(this.importInfos)
            .then(data => {
                if (data.error) {
                    this.stepErrors[0] = data.error;
                } else {
                    this.stepErrors[0] = null;
                    //this.columnsMapping = data;
                    this.columnsMappingProfiles = Object.keys(this.columnsMapping);
                    this.wizardEl.doNextStep();
                }
                this.cdRef.markForCheck();
            });
    
    }

    private updateColumnsMapping() {
        ImportCSVService.updateColumnsMapping(this.importInfos, this.columnsMapping)
            .then(data => {
                if (data.error) {
                    this.stepErrors[0] = data.error;
                } else {
                    this.stepErrors[0] = null;
                    this.wizardEl.doNextStep();
                    this.getClassesMapping()
                }
                this.cdRef.markForCheck();
            });
    }

    private getClassesMapping() {
        ImportCSVService.getClassesMapping(this.importInfos)
            .then(data => {
                if (data.error) {
                    this.stepErrors[0] = data.error;
                } else {
                    this.stepErrors[0] = null;
                    //this.classesMapping = data;
                    this.classesMappingProfiles = Object.keys(this.classesMappingProfiles);
                }
                this.cdRef.markForCheck();
            });
    }
    
    private updateClassesMapping() {
        ImportCSVService.updateClassesMapping(this.importInfos, this.classesMapping)
            .then(data => {
                if (data.error) {
                    this.stepErrors[0] = data.error;
                } else {
                    this.stepErrors[0] = null;
//                    this.classesMapping = data;
                    this.wizardEl.doNextStep();
                }
                this.cdRef.markForCheck();
            });
    }
}
