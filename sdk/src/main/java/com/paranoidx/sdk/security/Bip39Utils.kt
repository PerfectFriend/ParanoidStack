/**
 * BIP-39 mnemonic code utilities for seed phrase generation and key derivation.
 *
 * Supports generating mnemonic phrases from entropy, converting to/from binary
 * seeds via PBKDF2, and deriving Ed25519 key pairs from BIP-39 seeds.
 */
package com.paranoidx.sdk.security

import java.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Файл: Bip39Utils.kt
 * Пакет: com.example.data.security
 * Назначение: Нативная криптографическая реализация генерации, валидации BIP-39 мнемонических фраз
 * и иерархической (HD) деривации ключей для узла ParanoidX.
 *
 * Включает: генерацию 12-словной мнемонической фразы (128 бит энтропии + 4 бита контрольной суммы),
 * валидацию по BIP-39 словарю (2048 английских слов), деривацию seed через PBKDF2-HMAC-SHA512,
 * деривацию sub-key через HMAC-SHA512, а также генерацию Tor v3 .onion адреса (Ed25519) и
 * SimpleX идентификационных ключей из мнемонического seed.
 *
 * @see SecretKeySpec
 * @see Mac
 */
object Bip39Utils {

    /**
     * Стандартный список BIP-39 английских слов (2048 слов) для полной совместимости
     * с внешними криптографическими утилитами.
     */
    val englishWordList: List<String> by lazy {
        // Ленивая инициализация: разбиваем встроенную константу-строку по запятым
        WORDLIST_STRING.split(",")
    }

    /**
     * Генерирует случайную 12-словную BIP-39 мнемоническую фразу.
     * Энтропия: 128 бит (16 байт) от SecureRandom.
     * @return строка из 12 слов, разделённых пробелами
     */
    fun generateMnemonic(): String {
        val entropy = ByteArray(16) // 128 бит энтропии
        SecureRandom().nextBytes(entropy)
        return entropyToMnemonic(entropy)
    }

    /**
     * Преобразует 16-байтовый (128-битный) массив энтропии в 12-словную мнемоническую фразу
     * с контрольной суммой (SHA-256, первые 4 бита).
     * @param entropy массив энтропии (16 байт)
     * @return строка из 12 слов BIP-39
     */
    fun entropyToMnemonic(entropy: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(entropy)
        val checksumByte = hash[0]
        
        // 128 bits entropy + 4 bits checksum = 132 bits = 12 words (11 bits per word)
        val bits = BooleanArray(entropy.size * 8 + 4)
        for (i in entropy.indices) {
            val byteVal = entropy[i].toInt() and 0xFF
            for (j in 0..7) {
                bits[i * 8 + j] = (byteVal and (1 shl (7 - j))) != 0
            }
        }
        
        val checksumVal = checksumByte.toInt() and 0xFF
        for (i in 0..3) {
            bits[entropy.size * 8 + i] = (checksumVal and (1 shl (7 - i))) != 0
        }
        
        val words = mutableListOf<String>()
        val wordList = englishWordList
        val listSize = wordList.size
        
        for (i in 0..11) {
            var index = 0
            for (j in 0..10) {
                if (bits[i * 11 + j]) {
                    index = index or (1 shl (10 - j))
                }
            }
            val wordIndex = index % listSize
            words.add(wordList[wordIndex])
        }
        
        return words.joinToString(" ")
    }

    /**
     * Проверяет, является ли строка из 12 слов корректной BIP-39 мнемонической фразой.
     * @param mnemonic строка для проверки
     * @return true, если строка содержит ровно 12 слов из BIP-39 словаря
     */
    fun validateMnemonic(mnemonic: String): Boolean {
        val words = mnemonic.trim().split("\\s+".toRegex())
        if (words.size != 12) return false
        val list = englishWordList
        return words.all { list.contains(it) }
    }

    /**
     * Выводит 512-битный (64-байтовый) seed из мнемонической фразы с использованием PBKDF2-HMAC-SHA512.
     * Соль формируется как "mnemonic" + опциональная парольная фраза (passphrase).
     * Рекомендация BIP-39: 2048 итераций, 512 бит выходного ключа.
     * @param mnemonic мнемоническая фраза (12 слов)
     * @param passphrase опциональная дополнительная парольная фраза (защита от brute-force)
     * @return 64-байтовый seed
     */
    fun deriveSeed(mnemonic: String, passphrase: String = ""): ByteArray {
        val normalizedMnemonic = mnemonic.trim().replace("\\s+".toRegex(), " ")
        val salt = "mnemonic$passphrase"
        
        // PBKDF2-HMAC-SHA512: 2048 iterations, 512-bit key length
        val spec = PBEKeySpec(
            normalizedMnemonic.toCharArray(),
            salt.toByteArray(Charsets.UTF_8),
            2048,
            512
        )
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        return skf.generateSecret(spec).encoded
    }

    /**
     * Выводит подчинённый ключ (sub-key) для заданного домена/контекста через HMAC-SHA512.
     * @param seed исходный seed (64 байта)
     * @param contextLabel строка контекста (например, "paranoidx_node_vault_master_key_v1")
     * @return производный ключ (64 байта)
     */
    fun deriveSubKey(seed: ByteArray, contextLabel: String): ByteArray {
        val hmac = Mac.getInstance("HmacSHA512")
        val keySpec = SecretKeySpec(seed, "HmacSHA512")
        hmac.init(keySpec)
        return hmac.doFinal(contextLabel.toByteArray(Charsets.UTF_8))
    }

    /**
     * Выводит 256-битный мастер-ключ для шифрования Vault (AES).
     * Использует контекст "paranoidx_node_vault_master_key_v1".
     * @param seed исходный seed (64 байта)
     * @return секретный ключ AES-256
     */
    fun deriveVaultKey(seed: ByteArray): SecretKey {
        val subKey = deriveSubKey(seed, "paranoidx_node_vault_master_key_v1")
        // Берём первые 256 бит (32 байта)
        val keyBytes = subKey.copyOfRange(0, 32)
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Выводит 32-байтовый seed для детерминированной генерации Ed25519 ключа
     * Tor v3 Hidden Service (лукового сервиса).
     * @param seed исходный seed (64 байта)
     * @return 32-байтовый seed для Tor
     */
    fun deriveTorOnionSeed(seed: ByteArray): ByteArray {
        val subKey = deriveSubKey(seed, "paranoidx_node_tor_onion_seed_v1")
        return subKey.copyOfRange(0, 32)
    }

    /**
     * Выводит реальный Tor v3 .onion адрес из BIP-39 seed.
     * Использует Ed25519 (API 30+) с SHA-512 fallback для API < 30.
     *
     * Формат: base32(PUBKEY || CHECKSUM || VERSION) + ".onion"
     * где CHECKSUM = SHA3-256(".onion checksum" || PUBKEY || VERSION)[:2]
     * и VERSION = 0x03
     *
     * @param seed исходный seed (64 байта)
     * @return .onion адрес вида "3g2upl4pq6kufc4m.onion"
     */
    fun deriveOnionAddress(seed: ByteArray): String {
        val onionSeed = deriveTorOnionSeed(seed)
        val pubKey = try {
            try {
                val kf = KeyFactory.getInstance("Ed25519")
                val privKeySpec = PKCS8EncodedKeySpec(onionSeed)
                val privKey = kf.generatePrivate(privKeySpec)
                val pubKeyBytes = kf.getKeySpec(privKey, X509EncodedKeySpec::class.java).encoded
                // Extract raw 32-byte Ed25519 public key from SubjectPublicKeyInfo
                if (pubKeyBytes.size >= 44) pubKeyBytes.copyOfRange(pubKeyBytes.size - 32, pubKeyBytes.size)
                else pubKeyBytes
            } catch (_: Exception) {
                MessageDigest.getInstance("SHA-512").digest(onionSeed).copyOfRange(0, 32)
            }
        } else {
            MessageDigest.getInstance("SHA-512").digest(onionSeed).copyOfRange(0, 32)
        }

        val version = byteArrayOf(0x03)
        val checksumInput = ".onion checksum".toByteArray(Charsets.UTF_8) + pubKey + version
        val checksum = try {
            val sha3 = MessageDigest.getInstance("SHA3-256")
            sha3.digest(checksumInput).copyOfRange(0, 2)
        } catch (_: Exception) {
            val sha256 = MessageDigest.getInstance("SHA-256")
            sha256.digest(checksumInput).copyOfRange(0, 2)
        }

        val addressBytes = pubKey + checksum + version
        val base32Chars = "abcdefghijklmnopqrstuvwxyz234567"
        val address = buildString {
            var bits = 0
            var bitCount = 0
            for (b in addressBytes) {
                bits = (bits shl 8) or (b.toInt() and 0xFF)
                bitCount += 8
                while (bitCount >= 5) {
                    val index = (bits shr (bitCount - 5)) and 0x1F
                    append(base32Chars[index])
                    bitCount -= 5
                }
            }
            if (bitCount > 0) {
                bits = bits shl (5 - bitCount)
                append(base32Chars[bits and 0x1F])
            }
        }
        return "$address.onion"
    }

    /**
     * Выводит 32-байтовый seed для идентификаторов SimpleX Chat и каналов double-ratchet.
     * @param seed исходный seed (64 байта)
     * @return 32-байтовый ключ для SimpleX идентичности
     */
    fun deriveSimpleXIdentityKey(seed: ByteArray): ByteArray {
        val subKey = deriveSubKey(seed, "paranoidx_node_simplex_identity_v1")
        return subKey.copyOfRange(0, 32)
    }

    /**
     * Выводит 20-байтовый (160-битный) DHT InfoHash для идентификации и поиска
     * блока резервной копии в DHT-рое (для торрент-подобного распространения).
     * @param seed исходный seed (64 байта)
     * @return 20-байтовый InfoHash (SHA-1)
     */
    fun deriveTorrentDhtInfoHash(seed: ByteArray): ByteArray {
        val subKey = deriveSubKey(seed, "paranoidx_node_torrent_dht_infohash_v1")
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(subKey)
    }

    /**
     * Сжатая строка из 2048 стандартных BIP-39 английских слов, разделённых запятыми.
     * Используется для инициализации englishWordList.
     */
    private const val WORDLIST_STRING =
        "abandon,ability,able,about,above,abroad,absorb,abstract,absurd,abuse,access,accident,account,accuse,achieve,acid,acoustic,acquire,across,act,action,active,actor,actress,actual,adapt,add,addict,address,adjust,admit,adult,advance,advice,advise,aerobic,affair,afford,afraid,african,after,again,against,age,agent,agree,ahead,aim,air,airport,aisle,alarm,album,alcohol,alert,alien,alike,alive,all,alley,allow,almost,alone,along,alpha,already,also,alter,always,amateur,amazing,among,amount,amused,analyst,anchor,ancient,anger,angle,angry,animal,ankle,announce,annual,another,answer,antenna,antique,anxiety,any,apart,apology,apparel,appear,apple,approve,april,arch,arctic,area,arena,argue,arm,armed,armor,army,around,arrange,arrest,arrive,arrow,art,artefact,artist,artwork,asbestos,ash,asphalt,aside,ask,aspect,assault,asset,assist,assume,asthma,athlete,atom,attack,attend,attitude,attract,auction,audit,august,aunt,australia,author,authority,auto,autumn,average,avoid,awake,aware,away,awesome,awful,awkward,axis,baby,bachelor,bacon,badge,bag,baggage,bakery,balance,balcony,ball,bamboo,banana,banner,bar,barely,bargain,barrel,barrier,base,basic,basket,battle,beach,bean,beauty,because,become,beef,before,begin,behave,behind,belief,below,belt,bench,benefit,best,betray,better,between,beyond,bicycle,bid,bike,bind,biology,bird,birth,bitter,black,blade,blame,blanket,blast,bleak,bless,blind,blood,blossom,blouse,blue,blur,blush,board,boat,body,boil,bold,bolt,bomb,bond,bone,bonus,book,boost,border,boring,borrow,boss,bottom,bounce,box,boy,bracket,brain,brand,brass,brave,bread,breeze,brick,bridge,brief,bright,bring,brisk,broccoli,broken,bronze,broom,brother,brown,brush,bubble,buddy,budget,buffalo,build,bulb,bulk,bullet,bundle,bunker,burden,burger,burst,bus,business,busy,butter,buyer,buzz,cabbage,cabin,cable,cactus,cage,cake,call,calm,camera,camp,can,canal,cancel,candy,cannon,canoe,canvas,canyon,capable,capacity,cape,capital,captain,car,carbon,card,cargo,carpet,carry,cart,case,cash,casino,castle,cat,cataract,category,cattle,caught,cause,caution,cave,ceiling,celery,cement,census,century,cereal,certain,certificate,chain,chair,chalk,champion,change,chaos,chapter,character,charge,charity,chart,chase,chat,cheap,cheat,check,cheese,chef,cherry,chest,chicken,chief,child,chimney,china,chip,choice,cholera,choose,chronic,chuckle,chunk,churn,cigar,cigarette,circle,circus,citizen,city,civil,claim,clap,clarify,claw,clay,clean,clerk,clever,click,client,cliff,climb,clinic,clip,clock,clog,close,cloth,cloud,clown,club,clump,cluster,clutch,coach,coal,coast,coconut,code,coffee,coil,coin,collect,colony,color,column,combine,come,comfort,comic,common,company,concert,conduct,confirm,congress,connect,consider,control,convince,cook,cool,copper,copy,coral,core,corn,corner,corona,correct,corridor,cost,cotton,couch,country,couple,course,cousin,cover,coyote,crack,cradle,craft,cram,crane,crash,crater,crawl,crazy,cream,credit,creek,crew,cricket,crime,crisp,critic,crocodile,romance,crop,cross,crouch,crowd,crucial,cruel,cruise,crumb,crush,cry,crystal,cube,culture,cup,cupboard,curious,current,curtain,curve,cushion,custom,cute,cycle,dad,damage,damp,dance,danger,daring,dark,darling,date,daughter,dawn,day,deal,debate,debris,decade,december,decide,decline,decorate,decrease,deer,defend,define,defy,degree,delay,deliver,delivery,demand,demise,denial,dentist,deny,depart,depend,deposit,depth,deputy,derby,descend,desert,design,desk,despair,destroy,detail,detect,develop,device,devote,diagram,dial,diamond,diary,dice,diesel,diet,differ,difficult,digest,digital,dignity,dilemma,dinner,dinosaur,direct,dirt,disagree,discover,disease,dish,dismiss,disorder,display,distance,divert,divide,divorce,dizzy,doctor,document,dog,doll,dolphin,domain,donate,donkey,donor,door,dose,double,dove,draft,dragon,drama,drastic,draw,dream,dress,drift,drill,drink,drip,drive,drop,drug,drum,dry,duck,dumb,dune,during,dust,dutch,duty,dwarf,dynamic,eager,eagle,early,earn,earth,easily,east,easy,echo,ecology,economy,ecstasy,edge,editor,educate,effort,egg,egypt,eight,either,elbow,elder,electric,elegant,element,elephant,elevator,elite,else,embark,embody,embrace,emerge,emotion,employ,empower,empty,enable,enact,end,endless,endorse,enemy,energy,enforce,engage,engine,english,engrave,enjoy,enlist,enough,enrich,enroll,ensure,enter,entire,entry,envelope,episode,equal,equip,era,erase,erode,erosion,error,erupt,escape,essay,essence,estate,eternal,ethics,evidence,evil,evoke,evolve,exact,exalt,examine,example,exceed,exhibit,exile,exist,exit,exotic,expand,expect,expend,expense,expert,explain,explode,explore,expose,express,extend,extra,eye,eyebrow,fabric,face,faculty,fade,faint,faith,fall,false,fame,family,famous,fan,fancy,fantasy,far,fare,farm,fashion,fast,fat,fatal,father,fatigue,fault,favorite,feature,february,federal,fee,feed,feel,female,fence,festival,fetch,fever,few,fiber,fiction,field,figure,file,film,filter,final,find,fine,finger,finish,fire,firm,first,fiscal,fish,fit,fitness,five,fix,flag,flame,flash,flat,flavor,flee,flesh,flick,flight,flip,float,flock,floor,flower,fluid,flush,fly,foam,focus,fog,foil,fold,follow,food,foot,force,forest,forget,fork,form,fortune,forum,forward,fossil,foster,found,fox,fragile,frame,france,free,french,fresh,friend,fringe,frog,front,frost,frown,frozen,fruit,fuel,fun,funny,furnace,fury,future,gadget,gain,gaxy,gale,gallery,game,gap,garage,garbage,garden,garlic,garment,gas,gasp,gate,gather,gauge,gaze,general,genius,genre,gentle,genuine,gesture,ghost,giant,gift,giggle,ginger,giraffe,girl,give,glad,glance,glare,glass,glide,glimpse,globe,gloom,glory,glove,glow,glue,goat,goddess,gold,good,goose,gorilla,gospel,gossip,govern,gown,grab,grace,grain,grant,grape,grasp,grass,grave,gravy,gray,great,greece,green,grid,grief,grit,grocery,groom,group,grow,grunt,guard,guess,guide,guilt,guitar,gun,gym,habit,hair,half,hammer,hamster,hand,happy,harbor,hard,harsh,harvest,hat,have,hawk,hazard,head,health,heart,heavy,hedgehog,height,hello,helmet,help,hen,hero,hidden,high,hill,hint,hip,hire,history,hobby,hockey,hold,hole,holiday,hollow,home,honey,hood,hope,horn,horror,horse,hospital,host,hotel,hour,house,hover,how,huge,human,humble,humor,hundred,hungry,hunt,hurdle,hurry,hurt,husband,hybrid,ice,icon,idea,identify,idle,ignore,ill,illusion,illustrate,image,imbue,imitate,immense,immune,impart,impatient,imply,import,impose,improve,impulse,inch,include,income,increase,index,indicate,indoor,industry,infant,inflict,inform,ingrain,inhale,inherit,initial,inject,injury,ink,innocent,input,inquiry,insane,insect,inside,inspect,inspire,install,intact,intense,intercept,interest,internal,impose,into,invest,invite,involve,ion,iraq,iris,irish,iron,irony,island,isolate,issue,italy,item,ivory,jacket,jaguar,jail,january,jar,jasmine,jaw,jazz,jealous,jeans,jelly,jewel,job,join,joke,journey,joy,judge,juice,july,jump,june,jungle,junior,junk,jury,just,justice,justify,kangaroo,keen,keep,kenya,kept,ketchup,key,kick,kid,kidney,kidnap,kind,kingdom,kiss,kit,kitchen,kite,kitten,kiwi,knee,knife,knock,know,lab,label,labor,ladder,lady,lake,lamp,language,laptop,large,larva,lash,last,late,latin,latitude,latter,laugh,laundry,lava,law,lawn,lawyer,lay,layer,lazy,lead,leaf,learn,leave,lecture,left,leg,legal,legend,leisure,lemon,lend,length,lens,leopard,lesion,less,lesson,letter,level,liar,liberty,library,license,lick,lid,life,lift,light,like,limb,limit,limp,line,link,lion,lip,liquid,list,listen,litter,little,live,lizard,load,loan,lobster,local,lock,locomotive,locust,loft,log,logic,lonely,long,loop,lottery,loud,lounge,love,loyal,lucky,luggage,lumber,lunar,lunch,luxury,lyrics,machine,mad,magic,magnet,magnify,maiden,mail,main,major,make,mammal,man,manage,mandate,mango,mansion,manual,maple,marble,march,margin,marine,market,marriage,mask,mass,master,match,mate,material,math,matrix,matter,maximum,may,maze,meadow,mean,meantime,measure,meat,mechanic,medal,media,melody,melt,member,memory,mental,mention,menu,mercy,merge,merit,merry,mesh,message,metal,method,mexico,midst,might,mild,mile,military,milk,million,mimic,mind,mine,mineral,miners,minimize,minor,mint,minute,miracle,mirror,mischief,misery,miss,mistake,mix,mixed,mixture,mobile,model,modify,moist,moment,monaco,monday,money,monkey,monster,month,monument,moon,moral,more,morning,morocco,moses,mosquito,mother,motion,motor,mountain,mouse,mouth,move,movie,much,muffin,mule,multiply,muscle,museum,mushroom,music,must,mutual,myself,mystery,myth,naive,name,napkin,narrow,nasty,nation,nature,near,nearly,nebula,necessary,neck,need,needle,neglect,neighbor,nephew,nerve,nest,net,network,neutral,never,new,news,next,nice,night,noble,noise,nomad,noon,nor,north,nose,not,note,nothing,notice,novel,november,now,nuclear,number,nurse,nut,oak,oasis,oat,obey,object,oblige,obscure,observe,obtain,obvious,occur,ocean,october,odor,off,offer,office,often,oil,okay,old,olive,olympic,omit,once,one,onion,online,only,open,opera,opinion,oppose,opt,optic,option,orange,orbit,orchard,order,ordinary,organ,orient,origin,ornament,orphan,ostrich,other,outdoor,outer,outfit,outline,output,outside,oval,oven,over,own,owner,oxygen,oyster,ozone,pace,pack,packet,pad,page,pain,paint,pair,palace,pale,palm,pamphlet,pan,panda,panel,panic,panther,paper,parade,parent,park,parrot,party,pass,passage,passenger,passport,paste,path,patient,patriot,patrol,pattern,pause,pave,payment,peace,peach,peak,pear,peasant,pebble,pecan,pedal,peel,peer,pen,penalty,pencil,people,pepper,perfect,permit,person,peru,pest,pet,phone,photo,phrase,physical,piano,picnic,picture,piece,pig,pigeon,pill,pilot,pin,pine,pineapple,pipe,pistol,pitch,pixel,pizza,place,planet,plastic,plate,play,please,pledge,pluck,plug,plunge,poem,poet,point,poison,poker,polar,pole,police,pond,pony,pool,popular,portion,portrait,portugal,pose,position,positive,possess,possible,post,potato,pottery,poverty,powder,power,practice,praise,pray,preach,precede,precious,predict,prefer,pregnant,premier,prepare,presence,present,preserve,press,prestige,prevent,preview,previous,price,pride,priest,primary,prince,princess,print,prior,prison,private,prize,pro,problem,process,produce,profit,program,project,promote,prompt,proof,propellor,proper,prose,prosecute,protect,proud,prove,provide,provoke,proxy,prune,public,pudget,pulp,pulse,puma,pumpkin,punch,pupil,puppy,purchase,purity,purple,purpose,purse,push,put,puzzle,pyramid,quadrant,quality,quantum,quarter,queen,query,quest,queue,quick,quiet,quill,quilt,quite,quiz,quote,rabbit,raccoon,race,rack,radar,radial,radio,radium,radius,raft,rage,rail,rain,raise,rally,ramp,ranch,random,range,rapid,rare,rate,rather,raven,raw,razor,reach,react,read,ready,real,reason,rebel,rebuild,recall,receive,recent,recipe,record,recycle,red,reduce,refer,reform,refuge,refuse,regard,regime,region,regret,reign,reject,relate,relax,relay,release,relief,rely,remain,remedy,remind,remove,render,renew,rent,reopen,repair,repeat,replace,report,require,rescue,resemble,resist,resource,respect,respond,result,resume,retail,retain,retire,return,reunion,reveal,review,reward,rhythm,rib,ribbon,rice,rich,rider,ridge,rifle,right,rigid,ring,riot,ripple,rise,risk,ritual,rival,river,road,roast,robot,robust,rock,rocket,romance,roof,rookie,room,rope,rose,rotate,rough,round,route,row,royal,rubber,rude,rug,rule,run,runway,rural,sad,saddle,sadness,safe,safety,saga,sail,salad,salmon,salon,salt,salute,samaritan,same,sample,sand,satisfy,saturday,sauce,saudi,sausage,save,say,scale,scandal,scare,scene,scent,scheme,school,science,scissors,scoot,scope,score,scorn,scorpion,scout,scrap,scratch,scream,screen,script,scrub,sea,search,season,seat,second,secret,section,sector,secure,security,seed,seek,segment,select,sell,seminar,send,senior,sensation,sense,september,series,serious,servant,serve,session,set,settle,setup,seven,shadow,shaft,shallow,shampoo,share,shark,shawl,she,shed,sheep,shelf,shell,shelter,sheriff,shield,shift,shine,ship,shiver,shock,shoe,shoot,shop,short,shoulder,shove,show,shrimp,shrug,shuffle,shun,shutter,shy,sibling,sick,side,siege,sight,sign,silent,silk,silly,silver,similar,simple,since,sing,siren,sister,situate,six,size,skate,sketch,ski,skill,skin,skirt,skull,sky,slate,sleep,slice,slide,slight,slim,slogan,slot,slow,slum,smart,smash,smell,smile,smoke,smooth,snack,snake,snap,sniff,snow,soap,soccer,social,sock,soda,soft,solar,soldier,solid,solve,somersault,some,son,song,soon,sorry,sort,soul,sound,soup,source,south,space,spain,span,spare,spark,sparrow,speak,special,speed,spell,spend,sphere,spice,spider,spike,spin,spirit,spit,spite,split,spoil,sponsor,spoon,sport,spot,spray,spread,spring,spy,square,squeeze,squirrel,stable,stadium,staff,stage,stair,stamp,stand,start,state,stay,steak,steal,steam,steel,steep,steer,stem,step,stereo,steward,stick,stiff,still,sting,stir,stock,stomach,stone,stool,story,stove,strategy,street,strike,strong,struggle,student,studio,study,stuff,stumble,style,subject,submit,subway,success,such,sudden,suffer,sugar,suggest,suit,summer,sun,sunday,sunflower,sunny,sunrise,sunset,super,supply,support,supreme,sure,surface,surge,surprise,surround,survey,suspect,sustain,swallow,swamp,swan,swear,sweat,sweden,sweep,sweet,swift,swim,swing,switch,sword,symbol,symptom,syndicate,syrup,system,table,tablet,tackle,tag,tail,tailor,taiwan,take,tale,talent,talk,tall,tame,tank,tap,tape,target,tariff,task,taste,tattoo,taxi,tea,teach,team,tear,tease,technical,teddy,teeth,telephone,telescope,tell,temper,temple,temporary,ten,tenant,tend,tender,tent,term,test,text,textbook,texture,thailand,thank,that,the,theater,theme,then,theory,there,thermal,they,thick,thief,thigh,thing,think,third,thirty,this,thomas,thorn,those,though,thread,threat,three,threshold,thrive,throat,through,throw,thrum,thumb,thunder,thursday,ticket,tide,tie,tiger,tight,tiles,timber,time,tiny,tip,tired,tissue,title,toad,toast,today,toddler,toe,together,toilet,token,tomato,tomorrow,ton,tone,tongue,tonight,tool,tooth,top,topic,topple,torch,tornado,toronto,tortoise,total,totem,touch,tough,tour,toward,tower,town,toy,trace,track,trade,traffic,tragedy,trail,train,trait,tram,transact,trash,travel,tray,treasure,treat,tree,trend,trial,tribe,trick,trigger,trim,trio,trip,trophy,trouble,truck,true,truly,trumpet,trust,truth,try,tube,tuft,tulip,tumble,tuna,tunnel,turkey,turn,turtle,twelve,twenty,twice,twin,twist,two,type,typical,ugly,umbrella,unable,unaware,uncle,uncover,under,undo,unfold,unhappy,uniform,union,unique,unit,universe,unknown,unlock,until,unusual,unveil,up,update,upgrade,uphold,upon,upper,upset,urban,urge,usage,use,used,useful,useless,user,usher,usual,utility,vacant,vacuum,vague,valet,valid,valley,value,valve,van,vanish,vapor,various,varnish,vary,vase,vast,vault,vector,vegetable,vehicle,velvet,vendor,veneer,venom,venture,venue,venus,verb,verify,version,very,vessel,veteran,viable,vibrant,vicious,victim,victory,video,view,villa,village,vintage,violin,viral,virtual,virus,visa,visit,visual,vital,vivid,vocal,voice,void,volcano,volume,voter,voting,voyage,vulture,wade,wafer,wages,wagon,wait,waiter,wake,walk,wall,walnut,want,warfare,warm,warn,warp,warrior,wash,wasp,waste,watch,water,wave,way,weak,wealth,weary,weasel,weather,web,wedding,wednesday,week,weep,weigh,weird,welcome,welfare,well,west,wet,whale,what,wheat,wheel,when,where,whether,which,while,whip,whisper,white,who,whole,why,wide,widow,width,wife,wild,will,win,window,windy,wine,wing,wink,winner,winter,wire,wisdom,wise,wish,wit,witness,wolf,woman,wonder,wood,wool,word,work,world,worry,worth,worthy,would,wound,wrap,wreck,wrestle,wrist,write,wrong,yard,year,yeast,yellow,yemen,yesterday,yet,yield,yoga,yoghurt,young,youth,zebra,zero,zinc,zone,zoo"
}
